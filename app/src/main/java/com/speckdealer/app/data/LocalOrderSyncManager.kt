package com.speckdealer.app.data

import android.content.Context
import android.os.NetworkOnMainThreadException
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val syncMutex = Mutex()
	private val running = AtomicBoolean(false)
	private var serverSocket: ServerSocket? = null
	private var acceptJob: Job? = null
	private var pollingJob: Job? = null
	private var syncJob: Job? = null
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
		scope.launch(Dispatchers.IO) {
			try {
				val socket = ServerSocket(port)
				serverSocket = socket
				updateState(
					SyncConnectionState(
						role = SyncRole.HOST,
						status = OrderSyncStatus.SYNCHRONIZED,
						host = resolveLocalHostAddress(),
						port = port,
						pairingCode = pairingCode,
						message = "Host aktiv"
					)
				)
				acceptJob?.cancel()
				acceptJob = scope.launch(Dispatchers.IO) { acceptClients(socket, pairingCode) }
			} catch (error: Exception) {
				Log.e(LOG_TAG, "Host konnte nicht gestartet werden", error)
				updateError("Host konnte nicht gestartet werden: ${friendlyHostError(error)}")
			}
		}
	}

	fun connectClient(host: String, port: Int, pairingCode: String) {
		disconnect(clearPersistedState = false)
		clientHost = host.trim()
		clientPort = port
		clientCode = pairingCode.trim()
		running.set(true)
		updateState(
			SyncConnectionState(
				role = SyncRole.CLIENT,
				status = OrderSyncStatus.SYNCING,
				host = clientHost,
				port = clientPort,
				pairingCode = clientCode,
				message = "Verbinde…"
			)
		)
		syncJob?.cancel()
		syncJob = scope.launch(Dispatchers.IO) {
			syncNowInternal()
			startPollingLoop()
		}
	}

	fun syncNow() {
		if (stateFlow.value.role != SyncRole.CLIENT) return
		if (!running.get()) return
		if (syncJob?.isActive == true) return
		syncJob = scope.launch(Dispatchers.IO) { syncNowInternal() }
	}

	fun disconnect(clearPersistedState: Boolean = true) {
		running.set(false)
		acceptJob?.cancel()
		acceptJob = null
		pollingJob?.cancel()
		pollingJob = null
		syncJob?.cancel()
		syncJob = null
		try {
			serverSocket?.close()
		} catch (_: Exception) {
		}
		serverSocket = null
		updateState(SyncConnectionState(role = SyncRole.NONE, status = OrderSyncStatus.OFFLINE, message = "Getrennt"))
		if (clearPersistedState) {
			clearPersistedState()
		} else {
			persistState(stateFlow.value)
		}
	}

	fun shutdown() {
		disconnect(true)
		scope.cancel()
	}

	private suspend fun acceptClients(socket: ServerSocket, expectedCode: String) {
		while (running.get()) {
			val client = try {
				socket.accept()
			} catch (error: Exception) {
				if (running.get()) {
					Log.e(LOG_TAG, "Host-Verbindung fehlgeschlagen", error)
					updateError("Host-Verbindung fehlgeschlagen")
				}
				break
			}
			handleIncomingClient(client, expectedCode)
		}
	}

	private suspend fun startPollingLoop() {
		pollingJob?.cancel()
		pollingJob = scope.launch(Dispatchers.IO) {
			while (running.get() && stateFlow.value.role == SyncRole.CLIENT) {
				delay(20_000)
				syncNow()
			}
		}
	}

	private suspend fun syncNowInternal() = syncMutex.withLock {
		val current = stateFlow.value
		if (current.role != SyncRole.CLIENT) return
		if (!running.get()) return
		updateState(current.copy(status = OrderSyncStatus.SYNCING, message = "Wird synchronisiert"))
		try {
			val host = current.host.trim()
			if (host.isBlank()) {
				throw IllegalStateException("Host-IP fehlt")
			}
			if (host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)) {
				throw IllegalStateException("127.0.0.1 ist als Host-IP auf dem Client ungültig")
			}
			val request = JSONObject().apply {
				put("type", "SYNC_REQUEST")
				put("deviceId", repository.localDeviceId())
				put("pairingCode", current.pairingCode)
				put("orders", JSONArray().apply {
					repository.loadAll().forEach { put(it.toJson()) }
				})
			}
			Socket().use { socket ->
				socket.soTimeout = 10_000
				socket.connect(InetSocketAddress(host, current.port), 10_000)
				val out = DataOutputStream(socket.getOutputStream())
				val bytes = request.toString().toByteArray(Charsets.UTF_8)
				if (bytes.size > MAX_MESSAGE_BYTES) {
					throw IllegalStateException("Nachricht zu groß")
				}
				out.writeInt(bytes.size)
				out.write(bytes)
				out.flush()
				val input = DataInputStream(socket.getInputStream())
				val len = input.readInt()
				if (len <= 0 || len > MAX_MESSAGE_BYTES) {
					throw IllegalStateException("Ungültige Antwortgröße")
				}
				val responseBytes = ByteArray(len)
				input.readFully(responseBytes)
				val response = JSONObject(String(responseBytes, Charsets.UTF_8))
				if (response.optString("type") != "SYNC_RESPONSE") {
					throw IllegalStateException("Ungültiger Antworttyp")
				}
				if (!response.optBoolean("ok", false)) {
					throw IllegalStateException(response.optString("message", "Sync fehlgeschlagen"))
				}
				val orders = response.optJSONArray("orders") ?: JSONArray()
				if (orders.length() > MAX_ORDERS_PER_SYNC) {
					throw IllegalStateException("Zu viele Bestellungen im Sync")
				}
				val incoming = mutableListOf<OrderRecord>()
				for (i in 0 until orders.length()) {
					incoming += OrderRecord.fromJson(orders.getJSONObject(i))
				}
				repository.upsertIncoming(incoming)
				updateState(stateFlow.value.copy(status = OrderSyncStatus.SYNCHRONIZED, message = "Synchronisiert"))
				persistState(stateFlow.value)
			}
		} catch (error: NetworkOnMainThreadException) {
			Log.e(LOG_TAG, "Netzwerk auf dem UI-Thread blockiert", error)
			updateError("Netzwerkzugriff auf dem UI-Thread")
		} catch (error: SocketTimeoutException) {
			Log.e(LOG_TAG, "Synchronisation Zeitüberschreitung", error)
			updateError("Zeitüberschreitung bei der Synchronisation.")
		} catch (error: UnknownHostException) {
			Log.e(LOG_TAG, "Host nicht erreichbar", error)
			updateError("Gerät nicht erreichbar. Prüfe IP-Adresse und WLAN-Verbindung.")
		} catch (error: NoRouteToHostException) {
			Log.e(LOG_TAG, "Keine Route zum Host", error)
			updateError("Beide Geräte sind nicht im selben Netzwerk oder der Router blockiert die Verbindung.")
		} catch (error: ConnectException) {
			Log.e(LOG_TAG, "Verbindung abgelehnt", error)
			updateError("Verbindung abgelehnt. Wurde auf Gerät 1 der Host gestartet?")
		} catch (error: SocketException) {
			Log.e(LOG_TAG, "Socket-Fehler während Synchronisation", error)
			updateError("Firewall oder Router blockiert den Port.")
		} catch (error: IllegalArgumentException) {
			Log.e(LOG_TAG, "Ungültige IP-Adresse oder Socket-Parameter", error)
			updateError("Falsche IP-Adresse.")
		} catch (error: IllegalStateException) {
			Log.e(LOG_TAG, "Ungültige Sync-Antwort", error)
			updateError(mapClientError(error.message.orEmpty()))
		} catch (error: Exception) {
			Log.e(LOG_TAG, "Synchronisation fehlgeschlagen", error)
			updateError("Synchronisation fehlgeschlagen")
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

	private fun updateState(state: SyncConnectionState) {
		stateFlow.value = state
		persistState(state)
	}

	private fun updateError(message: String) {
		updateState(stateFlow.value.copy(status = OrderSyncStatus.ERROR, message = message))
	}

	private fun mapClientError(message: String): String {
		val normalized = message.lowercase()
		return when {
			normalized.contains("gerätecode") -> "Gerätecode ist falsch."
			normalized.contains("ungültige antwortgröße") -> "Ungültige Antwort vom Host."
			normalized.contains("ungültiger antworttyp") -> "Ungültige Antwort vom Host."
			normalized.contains("zu viele bestellungen") -> "Datenfehler beim Synchronisieren."
			normalized.contains("host-ip fehlt") -> "Host-IP fehlt."
			normalized.contains("127.0.0.1") -> "Falsche IP-Adresse."
			else -> message.ifBlank { "Synchronisation fehlgeschlagen" }
		}
	}

	private fun friendlyHostError(error: Exception): String {
		return when (error) {
			is SocketException -> "Port nicht erreichbar oder bereits belegt."
			else -> error.message ?: "Host konnte nicht gestartet werden"
		}
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
		private const val LOG_TAG = "OrderSync"
	}
}
