package com.speckdealer.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

enum class SyncRole { NONE, HOST, CLIENT }

enum class OrderSyncStatus { SYNCHRONIZED, SYNCING, OFFLINE, ERROR }

data class SyncConnectionState(
	val role: SyncRole = SyncRole.NONE,
	val status: OrderSyncStatus = OrderSyncStatus.OFFLINE,
	val host: String = "",
	val port: Int = 0,
	val pairingCode: String = "",
	val message: String = ""
)

class LocalOrderSyncManager(
	context: Context,
	dataMode: String
) {
	private val repository = OrderSyncRepositoryRegistry.get(context, dataMode)
	private val stateFlow = MutableStateFlow(SyncConnectionState())
	private val running = AtomicBoolean(false)
	private var serverSocket: ServerSocket? = null
	private var acceptThread: Thread? = null
	private var clientLoopThread: Thread? = null
	private var clientHost: String = ""
	private var clientPort: Int = 0
	private var clientCode: String = ""
	private val preferences = context.getSharedPreferences("speckdealer_sync_$dataMode", Context.MODE_PRIVATE)

	fun state(): StateFlow<SyncConnectionState> = stateFlow

	init {
		restoreConnectionState()
	}

	fun localDeviceId(): String = repository.localDeviceId()

	fun startHost(port: Int, pairingCode: String) {
		disconnect(clearPersistedState = false)
		running.set(true)
		serverSocket = ServerSocket(port)
		val bindHost = resolveLocalHostAddress()
		stateFlow.value = SyncConnectionState(
			role = SyncRole.HOST,
			status = OrderSyncStatus.SYNCHRONIZED,
			host = bindHost,
			port = port,
			pairingCode = pairingCode,
			message = "Host aktiv"
		)
		persistState(stateFlow.value)
		acceptThread = Thread {
			while (running.get()) {
				try {
					val socket = serverSocket?.accept() ?: break
					handleIncomingClient(socket, pairingCode)
				} catch (_: Exception) {
					if (running.get()) {
						stateFlow.value = stateFlow.value.copy(status = OrderSyncStatus.ERROR, message = "Host-Verbindung fehlgeschlagen")
						persistState(stateFlow.value)
					}
				}
			}
		}.apply {
			isDaemon = true
			start()
		}
	}

	fun connectClient(host: String, port: Int, pairingCode: String) {
		disconnect(clearPersistedState = false)
		clientHost = host.trim()
		clientPort = port
		clientCode = pairingCode.trim()
		running.set(true)
		stateFlow.value = SyncConnectionState(
			role = SyncRole.CLIENT,
			status = OrderSyncStatus.SYNCING,
			host = clientHost,
			port = clientPort,
			pairingCode = clientCode,
			message = "Verbinde…"
		)
		persistState(stateFlow.value)
		syncNow()
		clientLoopThread = Thread {
			while (running.get() && stateFlow.value.role == SyncRole.CLIENT) {
				try {
					Thread.sleep(20_000)
					syncNow()
				} catch (_: InterruptedException) {
					break
				}
			}
		}.apply {
			isDaemon = true
			start()
		}
	}

	fun syncNow() {
		val current = stateFlow.value
		if (current.role != SyncRole.CLIENT) return
		stateFlow.value = current.copy(status = OrderSyncStatus.SYNCING, message = "Wird synchronisiert")
		try {
			if (current.host == "127.0.0.1" || current.host.equals("localhost", ignoreCase = true)) {
				stateFlow.value = current.copy(status = OrderSyncStatus.ERROR, message = "127.0.0.1 ist als Host-IP auf dem Client ungültig")
				persistState(stateFlow.value)
				return
			}
			val request = JSONObject().apply {
				put("type", "SYNC_REQUEST")
				put("deviceId", repository.localDeviceId())
				put("pairingCode", current.pairingCode)
				put("orders", JSONArray().apply {
					repository.loadAll().forEach { put(it.toJson()) }
				})
			}
			Socket(current.host, current.port).use { socket ->
				socket.soTimeout = 10_000
				val out = DataOutputStream(socket.getOutputStream())
				val bytes = request.toString().toByteArray(Charsets.UTF_8)
				if (bytes.size > MAX_MESSAGE_BYTES) throw IllegalStateException("Nachricht zu groß")
				out.writeInt(bytes.size)
				out.write(bytes)
				out.flush()

				val input = DataInputStream(socket.getInputStream())
				val len = input.readInt()
				if (len <= 0 || len > MAX_MESSAGE_BYTES) throw IllegalStateException("Ungültige Antwortgröße")
				val responseBytes = ByteArray(len)
				input.readFully(responseBytes)
				val response = JSONObject(String(responseBytes, Charsets.UTF_8))
				if (response.optString("type") != "SYNC_RESPONSE") throw IllegalStateException("Ungültiger Antworttyp")
				if (!response.optBoolean("ok", false)) throw IllegalStateException(response.optString("message", "Sync fehlgeschlagen"))
				val orders = response.optJSONArray("orders") ?: JSONArray()
				if (orders.length() > MAX_ORDERS_PER_SYNC) throw IllegalStateException("Zu viele Bestellungen im Sync")
				val incoming = mutableListOf<OrderRecord>()
				for (i in 0 until orders.length()) {
					incoming += OrderRecord.fromJson(orders.getJSONObject(i))
				}
				repository.upsertIncoming(incoming)
				stateFlow.value = stateFlow.value.copy(status = OrderSyncStatus.SYNCHRONIZED, message = "Synchronisiert")
				persistState(stateFlow.value)
			}
		} catch (e: SocketTimeoutException) {
			stateFlow.value = stateFlow.value.copy(status = OrderSyncStatus.OFFLINE, message = "Timeout bei Synchronisation")
			persistState(stateFlow.value)
		} catch (e: Exception) {
			stateFlow.value = stateFlow.value.copy(status = OrderSyncStatus.ERROR, message = e.message ?: "Synchronisation fehlgeschlagen")
			persistState(stateFlow.value)
		}
	}

	fun disconnect(clearPersistedState: Boolean = true) {
		running.set(false)
		acceptThread?.interrupt()
		acceptThread = null
		clientLoopThread?.interrupt()
		clientLoopThread = null
		serverSocket?.close()
		serverSocket = null
		stateFlow.value = SyncConnectionState(role = SyncRole.NONE, status = OrderSyncStatus.OFFLINE, message = "Getrennt")
		if (clearPersistedState) {
			clearPersistedState()
		} else {
			persistState(stateFlow.value)
		}
	}

	private fun handleIncomingClient(socket: Socket, expectedCode: String) {
		socket.use {
			it.soTimeout = 10_000
			val input = DataInputStream(it.getInputStream())
			val out = DataOutputStream(it.getOutputStream())
			val len = input.readInt()
			if (len <= 0 || len > MAX_MESSAGE_BYTES) {
				writeError(out, "Ungültige Nachrichtengröße")
				return
			}
			val bytes = ByteArray(len)
			input.readFully(bytes)
			val request = JSONObject(String(bytes, Charsets.UTF_8))
			if (request.optString("type") != "SYNC_REQUEST") {
				writeError(out, "Ungültiger Nachrichtentyp")
				return
			}
			if (request.optString("pairingCode") != expectedCode) {
				writeError(out, "Gerätecode ungültig")
				return
			}
			val incomingJson = request.optJSONArray("orders") ?: JSONArray()
			if (incomingJson.length() > MAX_ORDERS_PER_SYNC) {
				writeError(out, "Zu viele Bestellungen")
				return
			}
			val incoming = mutableListOf<OrderRecord>()
			for (i in 0 until incomingJson.length()) {
				incoming += OrderRecord.fromJson(incomingJson.getJSONObject(i))
			}
			repository.upsertIncoming(incoming)
			val response = JSONObject().apply {
				put("type", "SYNC_RESPONSE")
				put("ok", true)
				put("orders", JSONArray().apply { repository.loadAll().forEach { put(it.toJson()) } })
				put("serverTime", System.currentTimeMillis())
			}
			val payload = response.toString().toByteArray(Charsets.UTF_8)
			out.writeInt(payload.size)
			out.write(payload)
			out.flush()
		}
	}

	private fun writeError(out: DataOutputStream, message: String) {
		val response = JSONObject().apply {
			put("type", "SYNC_RESPONSE")
			put("ok", false)
			put("message", message)
		}
		val payload = response.toString().toByteArray(Charsets.UTF_8)
		out.writeInt(payload.size)
		out.write(payload)
		out.flush()
	}

	private fun persistState(state: SyncConnectionState) {
		preferences.edit()
			.putString("role", state.role.name)
			.putString("host", state.host)
			.putInt("port", state.port)
			.putString("code", state.pairingCode)
			.apply()
	}

	private fun restoreConnectionState() {
		val roleName = preferences.getString("role", SyncRole.NONE.name).orEmpty()
		val role = runCatching { SyncRole.valueOf(roleName) }.getOrDefault(SyncRole.NONE)
		val host = preferences.getString("host", "").orEmpty()
		val port = preferences.getInt("port", 0)
		val code = preferences.getString("code", "").orEmpty()
		when (role) {
			SyncRole.HOST -> if (port in 1024..65535 && code.length >= 4) {
				runCatching { startHost(port, code) }
			}
			SyncRole.CLIENT -> if (host.isNotBlank() && port in 1024..65535 && code.length >= 4) {
				runCatching { connectClient(host, port, code) }
			}
			SyncRole.NONE -> Unit
		}
	}

	private fun clearPersistedState() {
		preferences.edit().clear().apply()
	}

	private fun resolveLocalHostAddress(): String {
		return runCatching {
			Collections.list(NetworkInterface.getNetworkInterfaces())
				.filter { it.isUp && !it.isLoopback }
				.flatMap { Collections.list(it.inetAddresses) }
				.firstOrNull { address ->
					address is InetAddress && !address.isLoopbackAddress && address.hostAddress?.contains(':') == false
				}
				?.hostAddress
		}.getOrNull().orEmpty().ifBlank { "0.0.0.0" }
	}

	companion object {
		private const val MAX_MESSAGE_BYTES = 512_000
		private const val MAX_ORDERS_PER_SYNC = 500
	}
}
