package com.speckdealer.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.speckdealer.app.data.ArchivedDailyReport
import com.speckdealer.app.data.ArchivedDailyReportStorage
import com.speckdealer.app.data.DailySalesStorage
import com.speckdealer.app.data.DataModeAwareStorageFactory
import com.speckdealer.app.data.DepositMovementStorage
import com.speckdealer.app.data.OrderStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyReportActivity : AppCompatActivity() {

	private lateinit var salesStorage: DailySalesStorage
	private lateinit var depositStorage: DepositMovementStorage
	private lateinit var orderStorage: OrderStorage
	private lateinit var archiveStorage: ArchivedDailyReportStorage
	private lateinit var completionPrefs: android.content.SharedPreferences
	private lateinit var dataMode: String
	private var completionInProgress = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_daily_report)

		dataMode = AppDataMode.resolve(intent.getStringExtra(AppDataMode.EXTRA_DATA_MODE))
		salesStorage = DataModeAwareStorageFactory.dailySalesStorage(this, dataMode)
		depositStorage = DataModeAwareStorageFactory.depositMovementStorage(this, dataMode)
		orderStorage = DataModeAwareStorageFactory.orderStorage(this, dataMode)
		archiveStorage = DataModeAwareStorageFactory.archivedDailyReportStorage(this, dataMode)
		val completionPrefsName = if (AppDataMode.isDev(dataMode)) "speckdealer_day_completion_dev" else "speckdealer_day_completion"
		completionPrefs = getSharedPreferences(completionPrefsName, MODE_PRIVATE)

		findViewById<Button>(R.id.reportCloseButton).setOnClickListener { finish() }
		findViewById<Button>(R.id.reportResetButton).setOnClickListener { confirmDayCompletionFlow() }
		findViewById<Button>(R.id.reportArchiveButton).setOnClickListener {
			startActivity(Intent(this, ArchivedReportsActivity::class.java).putExtra(AppDataMode.EXTRA_DATA_MODE, dataMode))
		}

		buildReport()
	}

	private fun buildReport() {
		val report = DailyReportBuilder.build(salesStorage.loadAll(), depositStorage.loadAll())
		findViewById<TextView>(R.id.reportSummaryText).text = report.summaryText
		findViewById<TextView>(R.id.reportBeverageVatText).text = report.beverageVatText
		findViewById<TextView>(R.id.reportFoodVatText).text = report.foodVatText
		findViewById<TextView>(R.id.reportSalesDetailsText).text = report.salesDetailsText
		findViewById<TextView>(R.id.reportEmployeeText).text = report.employeeText
		findViewById<TextView>(R.id.reportDepositSummaryText).text = report.depositSummaryText
		findViewById<TextView>(R.id.reportDepositBreakdownText).text = report.depositBreakdownText
	}

	private fun confirmDayCompletionFlow() {
		if (completionInProgress) return
		AlertDialog.Builder(this)
			.setTitle("Tagesabschluss")
			.setMessage("Soll der Tag archiviert werden?")
			.setPositiveButton("Ja") { _, _ -> archiveAndResetDay() }
			.setNeutralButton("Nein") { _, _ -> confirmResetWithoutArchive() }
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun archiveAndResetDay() {
		if (completionInProgress) return
		completionInProgress = true
		var completionTransactionId: String? = null

		try {
			val pendingId = completionPrefs.getString(KEY_PENDING_COMPLETION_ID, "").orEmpty()
			if (pendingId.isNotBlank()) {
				val existingArchived = archiveStorage.findByCompletionTransactionId(pendingId)
				if (existingArchived != null) {
					resetActiveDayData()
					clearPendingCompletionId()
					buildReport()
					Snackbar.make(
						findViewById(android.R.id.content),
						"Archivierung war bereits abgeschlossen. Tagesdaten wurden nun zurückgesetzt.",
						Snackbar.LENGTH_LONG
					).show()
					return
				}
				clearPendingCompletionId()
			}

			val sales = salesStorage.loadAll()
			val deposits = depositStorage.loadAll()
			val orders = orderStorage.loadAll()
			val businessDate = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
			val archivedAt = System.currentTimeMillis()
			completionTransactionId = DailyReportArchiveBuilder.buildCompletionTransactionId(
				businessDate = businessDate,
				records = sales,
				depositMovements = deposits,
				orders = orders
			)
			completionPrefs.edit().putString(KEY_PENDING_COMPLETION_ID, completionTransactionId).apply()

			val archivedReport = DailyReportArchiveBuilder.buildArchivedReport(
				businessDate = businessDate,
				archivedAt = archivedAt,
				completionTransactionId = completionTransactionId,
				records = sales,
				depositMovements = deposits,
				orders = orders
			)
			validateArchivedSnapshot(archivedReport)

			archiveStorage.saveIfAbsent(archivedReport)
			val verified = archiveStorage.findByCompletionTransactionId(completionTransactionId)
				?: throw IllegalStateException("Archivierung konnte nicht verifiziert werden")
			validateArchivedSnapshot(verified)

			resetActiveDayData()
			clearPendingCompletionId()
			buildReport()
			Snackbar.make(
				findViewById(android.R.id.content),
				"Tagesabschluss wurde archiviert und zurückgesetzt.",
				Snackbar.LENGTH_LONG
			).show()
		} catch (_: Exception) {
			val txId = completionTransactionId ?: completionPrefs.getString(KEY_PENDING_COMPLETION_ID, "").orEmpty()
			val archivedEntry = archiveStorage.findByCompletionTransactionId(txId)
			if (archivedEntry != null) {
				Snackbar.make(
					findViewById(android.R.id.content),
					"Archivierung ist gespeichert, aber der Reset konnte nicht vollständig abgeschlossen werden. Bitte Vorgang erneut ausführen.",
					Snackbar.LENGTH_LONG
				).show()
			} else {
				clearPendingCompletionId()
				Snackbar.make(
					findViewById(android.R.id.content),
					"Archivierung fehlgeschlagen. Tagesdaten wurden nicht zurückgesetzt.",
					Snackbar.LENGTH_LONG
				).show()
			}
		} finally {
			completionInProgress = false
		}
	}

	private fun confirmResetWithoutArchive() {
		AlertDialog.Builder(this)
			.setTitle("Tagesabschluss")
			.setMessage("Der aktuelle Tagesabschluss wird ohne Archivierung zurückgesetzt. Die Daten können danach nicht aus dem Archiv wiederhergestellt werden. Wirklich fortfahren?")
			.setPositiveButton("Trotzdem zurücksetzen") { _, _ ->
				try {
					resetActiveDayData()
					buildReport()
					Snackbar.make(
						findViewById(android.R.id.content),
						"Tagesabschluss wurde im Test-/Nicht-Archivierungsmodus zurückgesetzt.",
						Snackbar.LENGTH_LONG
					).show()
				} catch (_: Exception) {
					Snackbar.make(
						findViewById(android.R.id.content),
						"Reset ohne Archivierung fehlgeschlagen.",
						Snackbar.LENGTH_LONG
					).show()
				}
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun resetActiveDayData() {
		salesStorage.clearToday()
		depositStorage.clearToday()
		orderStorage.clear()
	}

	private fun clearPendingCompletionId() {
		completionPrefs.edit().remove(KEY_PENDING_COMPLETION_ID).apply()
	}

	private fun validateArchivedSnapshot(report: ArchivedDailyReport) {
		require(report.completionTransactionId.isNotBlank()) { "completionTransactionId fehlt" }
		require(report.businessDate.isNotBlank()) { "businessDate fehlt" }
		require(report.sourceSnapshotVersion > 0) { "Snapshot-Version ungültig" }
		require(report.depositBalanceCents == report.depositReceivedCents - report.depositReturnedCents) {
			"Pfandsaldo inkonsistent"
		}
	}

	companion object {
		private const val KEY_PENDING_COMPLETION_ID = "pending_completion_transaction_id"
	}
}
