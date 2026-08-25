package com.speckdealer.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.speckdealer.app.data.DiscoveredSyncDevice
import com.speckdealer.app.data.LocalOrderSyncManager
import com.speckdealer.app.data.OrderSyncStatus
import com.speckdealer.app.data.PairingRequirement
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceSyncActivity : AppCompatActivity() {

	private lateinit var manager: LocalOrderSyncManager
	private lateinit var statusText: TextView
	private lateinit var deviceInfoText: TextView
	private lateinit var hostInput: EditText
	private lateinit var portInput: EditText
	private lateinit var codeInput: EditText
	private lateinit var discoveryStateText: TextView
	private lateinit var lastSyncText: TextView
	private lateinit var adapter: DiscoveredDeviceAdapter
	private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_device_sync)

		val dataMode = AppDataMode.resolve(intent.getStringExtra(AppDataMode.EXTRA_DATA_MODE))
		manager = com.speckdealer.app.data.LocalOrderSyncRegistry.get(this, dataMode)

		statusText = findViewById(R.id.syncStatusText)
		deviceInfoText = findViewById(R.id.syncDeviceInfoText)
		hostInput = findViewById(R.id.syncHostInput)
		portInput = findViewById(R.id.syncPortInput)
		codeInput = findViewById(R.id.syncCodeInput)
		discoveryStateText = findViewById(R.id.syncDiscoveryText)
		lastSyncText = findViewById(R.id.syncLastSyncText)

		adapter = DiscoveredDeviceAdapter(
			onConnect = { device -> connectToDiscovered(device) },
			onRemovePairing = { device ->
				manager.removePairedDevice(device.deviceId)
				Snackbar.make(findViewById(android.R.id.content), "Gerät entfernt", Snackbar.LENGTH_LONG).show()
			}
		)
		findViewById<RecyclerView>(R.id.syncDiscoveredDevicesRecyclerView).apply {
			layoutManager = LinearLayoutManager(this@DeviceSyncActivity)
			adapter = this@DeviceSyncActivity.adapter
		}

		findViewById<Button>(R.id.syncRefreshDevicesButton).setOnClickListener { manager.refreshDiscovery() }
		findViewById<Button>(R.id.syncStartHostButton).setOnClickListener { startHost() }
		findViewById<Button>(R.id.syncConnectButton).setOnClickListener { connectClient() }
		findViewById<Button>(R.id.syncNowButton).setOnClickListener { manager.syncNow() }
		findViewById<Button>(R.id.syncDisconnectButton).setOnClickListener { manager.disconnect() }
		findViewById<Button>(R.id.syncCloseButton).setOnClickListener { finish() }

		deviceInfoText.text = "Gerät: ${manager.localDeviceDisplayName()}\nID: ${manager.localDeviceId()}"
		observeState()
		manager.refreshDiscovery()
	}

	private fun observeState() {
		lifecycleScope.launch {
			manager.state().collect { state ->
				val statusLabel = when (state.status) {
					OrderSyncStatus.SYNCHRONIZED -> "Synchronisiert"
					OrderSyncStatus.SYNCING -> "Wird synchronisiert"
					OrderSyncStatus.OFFLINE -> "Offline"
					OrderSyncStatus.ERROR -> "Fehler"
				}
				statusText.text = "$statusLabel${if (state.message.isNotBlank()) " – ${state.message}" else ""}"
				discoveryStateText.text = if (state.isDiscovering) {
					"Suche nach Geräten …"
				} else {
					"Gefundene Geräte: ${state.discoveredDevices.size}"
				}
				lastSyncText.text = if (state.lastSuccessfulSyncUtcMs > 0L) {
					"Letzte erfolgreiche Synchronisierung: ${timeFmt.format(Date(state.lastSuccessfulSyncUtcMs))}"
				} else {
					"Letzte erfolgreiche Synchronisierung: –"
				}
				adapter.submit(state.discoveredDevices)
				if (state.role == com.speckdealer.app.data.SyncRole.HOST && state.host.isNotBlank() && state.port > 0) {
					hostInput.setText(state.host)
					portInput.setText(state.port.toString())
					if (state.pairingCode.isNotBlank()) {
						codeInput.setText(state.pairingCode)
					}
				}
				if (state.pendingPairingDeviceId != null) {
					showPairingDialogIfNeeded(state.pendingPairingDeviceId)
				}
			}
		}
	}

	private fun startHost() {
		val port = portInput.text.toString().toIntOrNull()
		val code = codeInput.text.toString().trim()
		if (port == null || port !in 1024..65535) {
			Snackbar.make(findViewById(android.R.id.content), "Ungültiger Port.", Snackbar.LENGTH_LONG).show()
			return
		}
		if (code.length < 4) {
			Snackbar.make(findViewById(android.R.id.content), "Gerätecode muss mindestens 4 Zeichen haben.", Snackbar.LENGTH_LONG).show()
			return
		}
		runCatching {
			manager.startHost(port, code)
			Snackbar.make(findViewById(android.R.id.content), "Host gestartet auf Port $port.", Snackbar.LENGTH_LONG).show()
		}.onFailure {
			Snackbar.make(findViewById(android.R.id.content), "Host konnte nicht gestartet werden.", Snackbar.LENGTH_LONG).show()
		}
	}

	private fun connectClient() {
		val host = hostInput.text.toString().trim()
		val port = portInput.text.toString().toIntOrNull()
		val code = codeInput.text.toString().trim()
		if (host.isBlank()) {
			Snackbar.make(findViewById(android.R.id.content), "Host-IP fehlt.", Snackbar.LENGTH_LONG).show()
			return
		}
		if (port == null || port !in 1024..65535) {
			Snackbar.make(findViewById(android.R.id.content), "Ungültiger Port.", Snackbar.LENGTH_LONG).show()
			return
		}
		if (code.length < 4) {
			Snackbar.make(findViewById(android.R.id.content), "Gerätecode muss mindestens 4 Zeichen haben.", Snackbar.LENGTH_LONG).show()
			return
		}
		manager.connectClient(host, port, code)
	}

	private fun connectToDiscovered(device: DiscoveredSyncDevice) {
		val code = codeInput.text.toString().trim()
		if (code.length < 4) {
			Snackbar.make(findViewById(android.R.id.content), "Gerätecode muss mindestens 4 Zeichen haben.", Snackbar.LENGTH_LONG).show()
			return
		}
		val decision = manager.connectToDiscoveredDevice(device.deviceId, code)
		when (decision.requirement) {
			PairingRequirement.AUTO_RECONNECT -> {
				Snackbar.make(findViewById(android.R.id.content), "Verbinde mit bekanntem Gerät …", Snackbar.LENGTH_LONG).show()
			}
			PairingRequirement.REQUIRES_CONFIRMATION -> {
				Snackbar.make(findViewById(android.R.id.content), "Kopplung bestätigen", Snackbar.LENGTH_LONG).show()
			}
			PairingRequirement.INVALID -> {
				Snackbar.make(findViewById(android.R.id.content), decision.reason, Snackbar.LENGTH_LONG).show()
			}
		}
	}

	private var shownPendingPairingId: String? = null

	private fun showPairingDialogIfNeeded(deviceId: String) {
		if (shownPendingPairingId == deviceId) return
		shownPendingPairingId = deviceId
		val pending = manager.pendingPairingDevice()
		val label = pending?.displayName ?: deviceId
		AlertDialog.Builder(this)
			.setTitle("Kopplungsanfrage")
			.setMessage("Gerät bestätigen: $label")
			.setPositiveButton("Bestätigen") { _, _ ->
				if (manager.approvePendingPairing()) {
					Snackbar.make(findViewById(android.R.id.content), "Kopplung bestätigt", Snackbar.LENGTH_LONG).show()
				}
				shownPendingPairingId = null
			}
			.setNegativeButton("Ablehnen") { _, _ ->
				manager.rejectPendingPairing()
				shownPendingPairingId = null
			}
			.setOnDismissListener {
				shownPendingPairingId = null
			}
			.show()
	}

	override fun onDestroy() {
		super.onDestroy()
	}
}
