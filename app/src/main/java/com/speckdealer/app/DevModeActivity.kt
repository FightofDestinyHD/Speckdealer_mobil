package com.speckdealer.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.speckdealer.app.data.AppGraph
import com.speckdealer.app.data.ArchivedDailyReportStorage
import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.DataModeAwareStorageFactory
import com.speckdealer.app.data.DepositMovement
import com.speckdealer.app.data.DepositMovementType
import com.speckdealer.app.data.DailySalesStorage
import com.speckdealer.app.data.OrderStorage
import com.speckdealer.app.data.SaleRecord
import java.util.UUID

class DevModeActivity : AppCompatActivity() {

	private lateinit var salesStorage: DailySalesStorage
	private lateinit var depositStorage: com.speckdealer.app.data.DepositMovementStorage
	private lateinit var orderStorage: OrderStorage
	private lateinit var archiveStorage: ArchivedDailyReportStorage

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_dev_mode)

		salesStorage = DataModeAwareStorageFactory.dailySalesStorage(this, AppDataMode.MODE_DEV)
		depositStorage = DataModeAwareStorageFactory.depositMovementStorage(this, AppDataMode.MODE_DEV)
		orderStorage = DataModeAwareStorageFactory.orderStorage(this, AppDataMode.MODE_DEV)
		archiveStorage = DataModeAwareStorageFactory.archivedDailyReportStorage(this, AppDataMode.MODE_DEV)

		findViewById<TextView>(R.id.devDisclaimerText).text = DevModeConfig.DEV_PASSWORD_DISCLAIMER
		wireActions()
	}

	override fun onDestroy() {
		super.onDestroy()
		DevModeConfig.deactivateSession()
	}

	private fun wireActions() {
		findViewById<Button>(R.id.devStartTestSaleButton).setOnClickListener { startDevSales() }
		findViewById<Button>(R.id.devLoadTestArticlesButton).setOnClickListener { loadTestArticles() }
		findViewById<Button>(R.id.devDepositBottleButton).setOnClickListener { addDeposit("BOTTLE", "Flasche") }
		findViewById<Button>(R.id.devDepositGlassButton).setOnClickListener { addDeposit("GLASS", "Glas") }
		findViewById<Button>(R.id.devDepositPlateButton).setOnClickListener { addDeposit("PLATE", "Teller") }
		findViewById<Button>(R.id.devGenerateDailyDataButton).setOnClickListener { createDailyTestData() }
		findViewById<Button>(R.id.devDailyReportNoArchiveButton).setOnClickListener { openDailyReport() }
		findViewById<Button>(R.id.devDailyReportArchiveButton).setOnClickListener { openDailyReport() }
		findViewById<Button>(R.id.devOpenArchiveButton).setOnClickListener { openArchive() }
		findViewById<Button>(R.id.devTestPdfExportButton).setOnClickListener { openArchive() }
		findViewById<Button>(R.id.devShowDataButton).setOnClickListener { showDevDataSummary() }
		findViewById<Button>(R.id.devClearDataButton).setOnClickListener { confirmClearDevData() }
		findViewById<Button>(R.id.devExitButton).setOnClickListener {
			DevModeConfig.deactivateSession()
			finish()
		}
	}

	private fun startDevSales() {
		startActivity(Intent(this, SalesActivity::class.java).putExtra(AppDataMode.EXTRA_DATA_MODE, AppDataMode.MODE_DEV))
	}

	private fun loadTestArticles() {
		val repository = AppGraph.repository(this, AppDataMode.MODE_DEV)
		val existing = repository.getDepositArticles()
		if (existing.isNotEmpty()) {
			snack("Testartikel sind bereits vorhanden.")
			return
		}
		repository.saveArticle(com.speckdealer.app.data.ArticleEntity("DEV Pfand Flasche", CategoryType.PFAND.storageValue, 200, null, false, false, false, false, true, false, 0, 0, false, false, 0, 0))
		repository.saveArticle(com.speckdealer.app.data.ArticleEntity("DEV Pfand Glas", CategoryType.PFAND.storageValue, 100, null, false, false, false, false, true, false, 0, 0, false, false, 0, 0))
		repository.saveArticle(com.speckdealer.app.data.ArticleEntity("DEV Pfand Teller", CategoryType.PFAND.storageValue, 150, null, false, false, false, false, true, false, 0, 0, false, false, 0, 0))
		snack("DEV-Testartikel angelegt.")
	}

	private fun addDeposit(type: String, displayName: String) {
		val labels = arrayOf("Flasche", "Glas", "Teller")
		val types = arrayOf("BOTTLE", "GLASS", "PLATE")
		AlertDialog.Builder(this)
			.setTitle("Welcher Pfand soll zurückgegeben werden?")
			.setItems(labels) { _, selectedIndex ->
				val selectedType = types[selectedIndex]
				val selectedName = labels[selectedIndex]
				if (selectedType != type) {
					snack("Bitte die passende Pfandart über den jeweiligen Test-Button wählen.")
					return@setItems
				}
				showDepositQuantityDialog(selectedType, selectedName)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showDepositQuantityDialog(type: String, displayName: String) {
		val input = EditText(this).apply {
			inputType = InputType.TYPE_CLASS_NUMBER
			hint = "Menge"
		}
		AlertDialog.Builder(this)
			.setTitle("DEV-Pfandrückgabe")
			.setMessage("Pfandart: $displayName")
			.setView(input)
			.setPositiveButton("Weiter") { _, _ ->
				val quantity = input.text?.toString()?.trim()?.toIntOrNull()
				if (quantity == null || quantity <= 0) {
					snack("Bitte gültige Menge eingeben.")
					return@setPositiveButton
				}
				val unit = when (type) {
					"BOTTLE" -> 200L
					"GLASS" -> 100L
					else -> 150L
				}
				val total = unit * quantity
				AlertDialog.Builder(this)
					.setTitle("Bestätigen")
					.setMessage("$displayName: $quantity × ${MoneyValueService.formatCents(unit)} = ${MoneyValueService.formatCents(total)}")
					.setPositiveButton("Speichern") { _, _ ->
						depositStorage.appendMovement(
							DepositMovement(
								transactionId = "DEV-${UUID.randomUUID()}",
								depositType = type,
								displayName = displayName,
								quantity = quantity,
								unitAmountCents = unit,
								totalAmountCents = total,
								movementType = DepositMovementType.RETURNED
							)
						)
						snack("DEV-Pfand gespeichert.")
					}
					.setNegativeButton("Abbrechen", null)
					.show()
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun createDailyTestData() {
		val record = SaleRecord(
			articleName = "DEV Testverkauf",
			category = CategoryType.SOFTGETRAENKE.storageValue,
			servingType = "STANDARD",
			priceCents = 500,
			depositCents = 100,
			isEmployee = false,
			taxCategory = TaxCategory.BEVERAGE.name,
			taxRateBasisPoints = 1900,
			netAmountCents = 420,
			taxAmountCents = 80,
			grossAmountCents = 500,
			transactionId = "DEV-${UUID.randomUUID()}",
			recordId = "DEV-${UUID.randomUUID()}"
		)
		salesStorage.appendRecords(listOf(record))
		snack("DEV-Testdaten erzeugt.")
	}

	private fun openDailyReport() {
		startActivity(Intent(this, DailyReportActivity::class.java).putExtra(AppDataMode.EXTRA_DATA_MODE, AppDataMode.MODE_DEV))
	}

	private fun openArchive() {
		startActivity(Intent(this, ArchivedReportsActivity::class.java).putExtra(AppDataMode.EXTRA_DATA_MODE, AppDataMode.MODE_DEV))
	}

	private fun showDevDataSummary() {
		val sales = salesStorage.loadAll().size
		val deposits = depositStorage.loadAll().size
		val orders = orderStorage.loadAll().size
		val archives = archiveStorage.loadAll().size
		snack("DEV-Daten: Verkäufe=$sales, Pfand=$deposits, Bestellungen=$orders, Archive=$archives")
	}

	private fun confirmClearDevData() {
		AlertDialog.Builder(this)
			.setTitle("Testdaten löschen")
			.setMessage("Nur DEV-Testdaten werden gelöscht. Produktivdaten bleiben unverändert.")
			.setPositiveButton("Weiter") { _, _ ->
				AlertDialog.Builder(this)
					.setTitle("Wirklich löschen?")
					.setMessage("Dieser Vorgang löscht ausschließlich DEV-Testdaten.")
					.setPositiveButton("Ja, DEV-Daten löschen") { _, _ ->
						salesStorage.clearToday()
						depositStorage.clearToday()
						orderStorage.clear()
						archiveStorage.clearAll()
						snack("DEV-Testdaten gelöscht.")
					}
					.setNegativeButton("Abbrechen", null)
					.show()
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun snack(message: String) {
		Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
	}
}
