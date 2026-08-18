package com.speckdealer.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.speckdealer.app.data.LocalOrderSyncManager
import com.speckdealer.app.data.OrderSyncStatus
import kotlinx.coroutines.launch

class DeviceSyncActivity : AppCompatActivity() {

	private lateinit var manager: LocalOrderSyncManager
	private lateinit var statusText: TextView
	private lateinit var deviceInfoText: TextView
	private lateinit var hostInput: EditText
	private lateinit var portInput: EditText
	private lateinit var codeInput: EditText

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

		findViewById<Button>(R.id.syncStartHostButton).setOnClickListener { startHost() }
		findViewById<Button>(R.id.syncConnectButton).setOnClickListener { connectClient() }
		findViewById<Button>(R.id.syncNowButton).setOnClickListener { manager.syncNow() }
		findViewById<Button>(R.id.syncDisconnectButton).setOnClickListener { manager.disconnect() }
		findViewById<Button>(R.id.syncCloseButton).setOnClickListener { finish() }

		deviceInfoText.text = "Geräte-ID: ${manager.localDeviceId()}"
		observeState()
	}

	private fun observeState() {
		lifecycleScope.launch {
			manager.state().collect { state ->
				val statusLabel = when (state.status) {
					OrderSyncStatus.SYNCHRONIZED -> "Synchronisiert"
					OrderSyncStatus.SYNCING -> "Wird synchronisiert"
					OrderSyncStatus.OFFLINE -> "Wartet auf Synchronisation"
					OrderSyncStatus.ERROR -> "Fehler"
				}
				statusText.text = "$statusLabel${if (state.message.isNotBlank()) " – ${state.message}" else ""}"
				if (state.role == com.speckdealer.app.data.SyncRole.HOST && state.host.isNotBlank() && state.port > 0) {
					hostInput.setText(state.host)
					portInput.setText(state.port.toString())
					if (state.pairingCode.isNotBlank()) {
						codeInput.setText(state.pairingCode)
					}
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

	override fun onDestroy() {
		super.onDestroy()
	}
}
