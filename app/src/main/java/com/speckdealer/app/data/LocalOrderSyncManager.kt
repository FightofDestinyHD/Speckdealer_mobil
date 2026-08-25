package com.speckdealer.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.NetworkOnMainThreadException
import android.util.Log
import com.speckdealer.app.BuildConfig
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
	val message: String = "",
	val localDeviceName: String = "",
	val isDiscovering: Boolean = false,
	val discoveredDevices: List<DiscoveredSyncDevice> = emptyList(),
	val pendingPairingDeviceId: String? = null,
	val lastSuccessfulSyncUtcMs: Long = 0L
)

class LocalOrderSyncManager(
	context: Context,
	dataMode: String
) {
	private val appContext = context.applicationContext
	private val repository = OrderSyncRepositoryRegistry.get(appContext, dataMode)
	private val localDeviceName = normalizeDeviceName("Speckdealer ${Build.MODEL}")
	private val stateFlow = MutableStateFlow(SyncConnectionState(localDeviceName = localDeviceName))
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
	private val preferences = appContext.getSharedPreferences("speckdealer_sync_$dataMode", Context.MODE_PRIVATE)
	private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
	private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
	private var registrationListener: NsdManager.RegistrationListener? = null
	private var discoveryListener: NsdManager.DiscoveryListener? = null
	private val discoveryResolveListeners = mutableMapOf<String, NsdManager.ResolveListener>()
	private val knownPairedDeviceIds = linkedSetOf<String>()
	private var pendingPairingDevice: DiscoveredSyncDevice? = null
	private var pendingPairingCode: String = ""

	fun state(): StateFlow<SyncConnectionState> = stateFlow

	init {
		loadPairedDevices()
		restoreConnectionState()
		beginDiscovery()
		registerNetworkCallback()
	}

	fun localDeviceId(): String = repository.localDeviceId()

	fun localDeviceDisplayName(): String = localDeviceName

	fun approvePendingPairing(): Boolean {
		val pending = pendingPairingDevice ?: return false
		if (pending.deviceId.isBlank()) return false
		knownPairedDeviceIds += pending.deviceId
		persistPairedDevices()
		val shouldConnectNow = pending.host.isNotBlank() && pending.port in 1024..65535 && pendingPairingCode.length >= 4
		pendingPairingDevice = null
		updateState(stateFlow.value.copy(pendingPairingDeviceId = null, message = "Gerät gekoppelt: ${pending.displayName}"))
		if (shouldConnectNow) {
			val code = pendingPairingCode
			pendingPairingCode = ""
			connectClient(pending.host, pending.port, code)
		} else {
			pendingPairingCode = ""
		}
		return true
	}

	fun pendingPairingDevice(): DiscoveredSyncDevice? = pendingPairingDevice

	fun rejectPendingPairing() {
		pendingPairingDevice = null
		pendingPairingCode = ""
		updateState(stateFlow.value.copy(pendingPairingDeviceId = null, message = "Kopplung abgelehnt"))
	}

	fun removePairedDevice(deviceId: String) {
		if (deviceId.isBlank()) return
		knownPairedDeviceIds.remove(deviceId)
		persistPairedDevices()
		updateState(stateFlow.value.copy(message = "Gerät entfernt"))
	}

	fun refreshDiscovery() {
		beginDiscovery(forceRestart = true)
	}

	fun connectToDiscoveredDevice(deviceId: String, pairingCode: String): PairingDecision {
		val device = stateFlow.value.discoveredDevices.firstOrNull { it.deviceId == deviceId }
			?: return PairingDecision(PairingRequirement.INVALID, "Gerät nicht gefunden")
		val decision = evaluatePairingRequirement(
			remoteDeviceId = device.deviceId,
			remoteHost = device.host,
			remotePort = device.port,
			pairedDeviceIds = knownPairedDeviceIds
		)
		when (decision.requirement) {
			PairingRequirement.AUTO_RECONNECT -> {
				connectClient(device.host, device.port, pairingCode)
			}
			PairingRequirement.REQUIRES_CONFIRMATION -> {
				pendingPairingDevice = device
				pendingPairingCode = pairingCode
				updateState(stateFlow.value.copy(pendingPairingDeviceId = device.deviceId, message = "Kopplung bestätigen: ${device.displayName}"))
			}
			PairingRequirement.INVALID -> Unit
		}
		return decision
	}

	fun startHost(port: Int, pairingCode: String) {
		disconnect(clearPersistedState = false)
		running.set(true)
		scope.launch(Dispatchers.IO) {
			try {
				val socket = ServerSocket(port)
				serverSocket = socket
				val host = resolveLocalHostAddress()
				updateState(
					stateFlow.value.copy(
						role = SyncRole.HOST,
						status = OrderSyncStatus.SYNCHRONIZED,
						host = host,
						port = port,
						pairingCode = pairingCode,
						message = "Host aktiv"
					)
				)
				registerLocalSyncService(host = host, port = port)
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
			stateFlow.value.copy(
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
		unregisterLocalSyncService()
		try {
			serverSocket?.close()
		} catch (_: Exception) {
		}
		serverSocket = null
		updateState(stateFlow.value.copy(role = SyncRole.NONE, status = OrderSyncStatus.OFFLINE, host = "", port = 0, pairingCode = "", message = "Getrennt"))
		if (clearPersistedState) {
			clearPersistedState()
		} else {
			persistState(stateFlow.value)
		}
	}

	fun shutdown() {
		disconnect(true)
		stopDiscovery()
		unregisterNetworkCallback()
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
		if (!isLocalNetworkAvailable()) {
			updateError("Kein lokales WLAN verfügbar")
			return
		}
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
				put("deviceName", localDeviceName)
				put("appVersion", BuildConfig.VERSION_NAME)
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
				val now = System.currentTimeMillis()
				updateState(stateFlow.value.copy(status = OrderSyncStatus.SYNCHRONIZED, message = "Synchronisiert", lastSuccessfulSyncUtcMs = now))
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
			val remoteDeviceId = normalizeDeviceId(request.optString("deviceId", ""))
			if (remoteDeviceId.isBlank()) {
				writeError(out, "Geräte-ID fehlt")
				return
			}
			if (request.optString("pairingCode") != expectedCode) {
				writeError(out, "Gerätecode ungültig")
				return
			}
			val appVersion = request.optString("appVersion", "")
			if (appVersion.isBlank()) {
				writeError(out, "Inkompatible App-Version")
				return
			}
			if (!knownPairedDeviceIds.contains(remoteDeviceId)) {
				pendingPairingDevice = DiscoveredSyncDevice(
					deviceId = remoteDeviceId,
					displayName = normalizeDeviceName(request.optString("deviceName", "")),
					host = it.inetAddress?.hostAddress.orEmpty(),
					port = 0,
					lastSeenUtcMs = System.currentTimeMillis(),
					isPaired = false
				)
				pendingPairingCode = ""
				updateState(stateFlow.value.copy(pendingPairingDeviceId = remoteDeviceId, message = "Neue Kopplungsanfrage: ${pendingPairingDevice?.displayName}"))
				writeError(out, "Gerät nicht gekoppelt")
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
			.putString(KEY_ROLE, state.role.name)
			.putString(KEY_HOST, state.host)
			.putInt(KEY_PORT, state.port)
			.putString(KEY_CODE, state.pairingCode)
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
			normalized.contains("nicht gekoppelt") -> "Gerät ist noch nicht gekoppelt."
			normalized.contains("geräte-id fehlt") -> "Geräte-ID fehlt."
			normalized.contains("inkompatible app-version") -> "Geräteversionen sind nicht kompatibel."
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

	private var networkCallback: ConnectivityManager.NetworkCallback? = null

	private fun restoreConnectionState() {
		val roleName = preferences.getString(KEY_ROLE, SyncRole.NONE.name).orEmpty()
		val role = runCatching { SyncRole.valueOf(roleName) }.getOrDefault(SyncRole.NONE)
		val host = preferences.getString(KEY_HOST, "").orEmpty()
		val port = preferences.getInt(KEY_PORT, 0)
		val code = preferences.getString(KEY_CODE, "").orEmpty()
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
		preferences.edit().remove(KEY_ROLE).remove(KEY_HOST).remove(KEY_PORT).remove(KEY_CODE).apply()
	}

	private fun loadPairedDevices() {
		val stored = preferences.getStringSet(KEY_PAIRED_DEVICES, emptySet()).orEmpty()
		knownPairedDeviceIds.clear()
		knownPairedDeviceIds.addAll(stored.map { normalizeDeviceId(it) }.filter { it.isNotBlank() })
	}

	private fun persistPairedDevices() {
		preferences.edit().putStringSet(KEY_PAIRED_DEVICES, knownPairedDeviceIds.toSet()).apply()
	}

	private fun beginDiscovery(forceRestart: Boolean = false) {
		if (!isLocalNetworkAvailable()) {
			updateState(stateFlow.value.copy(isDiscovering = false, message = "Kein lokales Netzwerk"))
			return
		}
		if (forceRestart) {
			stopDiscovery()
		}
		if (discoveryListener != null) return
		val listener = object : NsdManager.DiscoveryListener {
			override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
				updateState(stateFlow.value.copy(isDiscovering = false, message = "Gerätesuche fehlgeschlagen ($errorCode)"))
				runCatching { nsdManager.stopServiceDiscovery(this) }
				discoveryListener = null
			}

			override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
				runCatching { nsdManager.stopServiceDiscovery(this) }
				discoveryListener = null
				updateState(stateFlow.value.copy(isDiscovering = false, message = "Gerätesuche gestoppt ($errorCode)"))
			}

			override fun onDiscoveryStarted(serviceType: String) {
				updateState(stateFlow.value.copy(isDiscovering = true, message = "Suche nach Geräten …"))
			}

			override fun onDiscoveryStopped(serviceType: String) {
				discoveryListener = null
				updateState(stateFlow.value.copy(isDiscovering = false, message = "Gerätesuche beendet"))
			}

			override fun onServiceFound(serviceInfo: NsdServiceInfo) {
				if (serviceInfo.serviceType != NSD_SERVICE_TYPE) return
				if (serviceInfo.serviceName == localServiceName()) return
				resolveDiscoveredService(serviceInfo)
			}

			override fun onServiceLost(serviceInfo: NsdServiceInfo) {
				val name = serviceInfo.serviceName.orEmpty()
				val updated = stateFlow.value.discoveredDevices.filterNot { it.displayName == name }
				updateState(stateFlow.value.copy(discoveredDevices = updated, message = "Gerät entfernt"))
			}
		}
		discoveryListener = listener
		runCatching {
			nsdManager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
		}.onFailure {
			discoveryListener = null
			updateState(stateFlow.value.copy(isDiscovering = false, message = "Gerätesuche nicht verfügbar"))
		}
	}

	private fun stopDiscovery() {
		val listener = discoveryListener ?: return
		runCatching { nsdManager.stopServiceDiscovery(listener) }
		discoveryListener = null
		updateState(stateFlow.value.copy(isDiscovering = false))
	}

	private fun registerLocalSyncService(host: String, port: Int) {
		unregisterLocalSyncService()
		val serviceInfo = NsdServiceInfo().apply {
			serviceName = localServiceName()
			serviceType = NSD_SERVICE_TYPE
			setPort(port)
			setAttribute("deviceId", repository.localDeviceId())
			setAttribute("deviceName", localDeviceName)
			setAttribute("appVersion", BuildConfig.VERSION_NAME)
			setAttribute("hostHint", host)
		}
		val listener = object : NsdManager.RegistrationListener {
			override fun onServiceRegistered(info: NsdServiceInfo) {
				updateState(stateFlow.value.copy(message = "Dienst im Netzwerk sichtbar"))
			}

			override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
				updateError("Dienstregistrierung fehlgeschlagen ($errorCode)")
			}

			override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

			override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
				updateError("Dienstabmeldung fehlgeschlagen ($errorCode)")
			}
		}
		registrationListener = listener
		runCatching { nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener) }
			.onFailure { updateError("Dienstregistrierung nicht möglich") }
	}

	private fun unregisterLocalSyncService() {
		val listener = registrationListener ?: return
		runCatching { nsdManager.unregisterService(listener) }
		registrationListener = null
	}

	private fun resolveDiscoveredService(serviceInfo: NsdServiceInfo) {
		val key = serviceInfo.serviceName.orEmpty().ifBlank { "${serviceInfo.serviceType}:${System.nanoTime()}" }
		if (discoveryResolveListeners.containsKey(key)) return
		val listener = object : NsdManager.ResolveListener {
			override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
				discoveryResolveListeners.remove(key)
			}

			override fun onServiceResolved(info: NsdServiceInfo) {
				discoveryResolveListeners.remove(key)
				val host = info.host?.hostAddress.orEmpty()
				val port = info.port
				val deviceId = normalizeDeviceId(info.attributes?.get("deviceId")?.toString(Charsets.UTF_8).orEmpty())
				if (host.isBlank() || port !in 1024..65535 || deviceId == repository.localDeviceId()) return
				val displayName = normalizeDeviceName(info.attributes?.get("deviceName")?.toString(Charsets.UTF_8).orEmpty().ifBlank { info.serviceName })
				val discovered = DiscoveredSyncDevice(
					deviceId = deviceId,
					displayName = displayName,
					host = host,
					port = port,
					lastSeenUtcMs = System.currentTimeMillis(),
					isPaired = knownPairedDeviceIds.contains(deviceId)
				)
				val merged = mergeVisibleDevices(
					existing = stateFlow.value.discoveredDevices,
					incoming = listOf(discovered),
					nowUtcMs = System.currentTimeMillis()
				)
				updateState(stateFlow.value.copy(discoveredDevices = merged, message = "${merged.size} Gerät(e) gefunden"))
			}
		}
		discoveryResolveListeners[key] = listener
		runCatching { nsdManager.resolveService(serviceInfo, listener) }
			.onFailure { discoveryResolveListeners.remove(key) }
	}

	private fun registerNetworkCallback() {
		val callback = object : ConnectivityManager.NetworkCallback() {
			override fun onAvailable(network: Network) {
				beginDiscovery(forceRestart = true)
			}

			override fun onLost(network: Network) {
				updateState(stateFlow.value.copy(isDiscovering = false, discoveredDevices = emptyList(), message = "Netzwerk getrennt"))
			}
		}
		networkCallback = callback
		runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }
	}

	private fun unregisterNetworkCallback() {
		val callback = networkCallback ?: return
		runCatching { connectivityManager.unregisterNetworkCallback(callback) }
		networkCallback = null
	}

	private fun isLocalNetworkAvailable(): Boolean {
		val active = connectivityManager.activeNetwork ?: return false
		val capabilities = connectivityManager.getNetworkCapabilities(active) ?: return false
		val hasWifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
		val hasEthernet = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
		return hasWifi || hasEthernet
	}

	private fun localServiceName(): String = "Speckdealer-${repository.localDeviceId().take(8)}"

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
		private const val NSD_SERVICE_TYPE = "_speckdealer-sync._tcp."
		private const val KEY_ROLE = "role"
		private const val KEY_HOST = "host"
		private const val KEY_PORT = "port"
		private const val KEY_CODE = "code"
		private const val KEY_PAIRED_DEVICES = "paired_device_ids"
	}
}
