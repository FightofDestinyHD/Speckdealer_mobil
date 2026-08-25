package com.speckdealer.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.speckdealer.app.data.DataModeAwareStorageFactory
import com.speckdealer.app.data.GlobalDepositSettings
import com.speckdealer.app.data.GlobalSettingsStorage

class SettingsActivity : AppCompatActivity() {

	private lateinit var globalSettingsStorage: GlobalSettingsStorage
	private lateinit var glassDepositInput: EditText
	private lateinit var bottleDepositInput: EditText
	private lateinit var plateDepositInput: EditText

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_settings)

		val dataMode = AppDataMode.resolve(intent.getStringExtra(AppDataMode.EXTRA_DATA_MODE))
		globalSettingsStorage = DataModeAwareStorageFactory.globalSettingsStorage(this, dataMode)

		glassDepositInput = findViewById(R.id.glassDepositInput)
		bottleDepositInput = findViewById(R.id.bottleDepositInput)
		plateDepositInput = findViewById(R.id.plateDepositInput)

		findViewById<Button>(R.id.settingsBackButton).setOnClickListener { finish() }
		findViewById<Button>(R.id.settingsSaveButton).setOnClickListener { saveSettings() }

		loadSettings()
	}

	private fun loadSettings() {
		val settings = globalSettingsStorage.loadDepositSettings()
		glassDepositInput.setText(formatCentsAsEuro(settings.glassDepositCents))
		bottleDepositInput.setText(formatCentsAsEuro(settings.bottleDepositCents))
		plateDepositInput.setText(formatCentsAsEuro(settings.plateDepositCents))
	}

	private fun saveSettings() {
		val glass = parseEuroToCents(glassDepositInput.text.toString())
		val bottle = parseEuroToCents(bottleDepositInput.text.toString())
		val plate = parseEuroToCents(plateDepositInput.text.toString())

		if (glass == null || bottle == null || plate == null) {
			AlertDialog.Builder(this)
				.setTitle("Eingabe prüfen")
				.setMessage("Bitte gültige Pfandbeträge eingeben (z. B. 0,50).")
				.setPositiveButton("OK", null)
				.show()
			return
		}

		globalSettingsStorage.saveDepositSettings(
			GlobalDepositSettings(
				glassDepositCents = glass,
				bottleDepositCents = bottle,
				plateDepositCents = plate
			)
		)

		AlertDialog.Builder(this)
			.setTitle("Gespeichert")
			.setMessage("Globale Pfandwerte wurden gespeichert.")
			.setPositiveButton("OK", null)
			.show()
	}

	private fun parseEuroToCents(value: String): Int? {
		val normalized = value.trim().replace(',', '.')
		if (normalized.isEmpty()) return 0
		val parsed = normalized.toBigDecimalOrNull() ?: return null
		if (parsed < java.math.BigDecimal.ZERO) return null
		return parsed.multiply(java.math.BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP).toInt()
	}

	private fun formatCentsAsEuro(cents: Int): String {
		return String.format(java.util.Locale.GERMANY, "%.2f", cents / 100.0)
	}
}
