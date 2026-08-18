package com.speckdealer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.speckdealer.app.data.ArchivedDailyReport
import com.speckdealer.app.data.ArchivedDailyReportStorage
import com.speckdealer.app.data.DataModeAwareStorageFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArchivedReportDetailActivity : AppCompatActivity() {

	private lateinit var archiveStorage: ArchivedDailyReportStorage
	private var currentReport: ArchivedDailyReport? = null
	private var pendingPdfFile: File? = null
	private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		val uri = result.data?.data
		if (result.resultCode != RESULT_OK || uri == null) {
			pendingPdfFile = null
			return@registerForActivityResult
		}
		savePdfToUri(uri)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_archived_report_detail)

		val dataMode = AppDataMode.resolve(intent.getStringExtra(AppDataMode.EXTRA_DATA_MODE))
		archiveStorage = DataModeAwareStorageFactory.archivedDailyReportStorage(this, dataMode)

		findViewById<Button>(R.id.archiveDetailCloseButton).setOnClickListener { finish() }
		findViewById<Button>(R.id.archiveExportPdfButton).setOnClickListener { generatePdfOnly() }
		findViewById<Button>(R.id.archiveSavePdfButton).setOnClickListener { exportPdfToLocalStorage() }
		findViewById<Button>(R.id.archiveSharePdfButton).setOnClickListener { sharePdf() }

		loadReport()
	}

	private fun loadReport() {
		val archiveId = intent.getStringExtra(EXTRA_ARCHIVE_ID).orEmpty()
		val report = archiveStorage.loadAll().firstOrNull { it.id == archiveId }
		if (report == null) {
			Snackbar.make(findViewById(android.R.id.content), "Archivdatensatz nicht gefunden.", Snackbar.LENGTH_LONG).show()
			finish()
			return
		}
		currentReport = report
		bind(report)
	}

	private fun bind(report: ArchivedDailyReport) {
		val archivedAt = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date(report.archivedAt))
		findViewById<TextView>(R.id.archiveDetailHeaderText).text =
			"Archiv-ID: ${report.id}\nGeschäftsdatum: ${report.businessDate}\nArchiviert am: $archivedAt\nSnapshot-Version: ${report.sourceSnapshotVersion}"

		findViewById<TextView>(R.id.archiveDetailSummaryText).text = buildString {
			appendLine("Gesamtumsatz: ${MoneyValueService.formatCents(report.totalRevenueCents)}")
			appendLine("Getränke Netto: ${MoneyValueService.formatCents(report.beverageNetCents)}")
			appendLine("Getränke MwSt. 19 %: ${MoneyValueService.formatCents(report.beverageVatCents)}")
			appendLine("Getränke Brutto: ${MoneyValueService.formatCents(report.beverageGrossCents)}")
			appendLine("Speisen Netto: ${MoneyValueService.formatCents(report.foodNetCents)}")
			appendLine("Speisen MwSt. 7 %: ${MoneyValueService.formatCents(report.foodVatCents)}")
			append("Speisen Brutto: ${MoneyValueService.formatCents(report.foodGrossCents)}")
		}

		findViewById<TextView>(R.id.archiveDetailDepositText).text = buildString {
			appendLine("Pfand erhalten: ${MoneyValueService.formatCents(report.depositReceivedCents)}")
			appendLine("Pfand zurückgegeben: ${MoneyValueService.formatCents(report.depositReturnedCents)}")
			appendLine("Pfand-Saldo: ${MoneyValueService.formatCents(report.depositBalanceCents)}")
			report.depositSummaries.forEach {
				appendLine("${it.displayName}: ${it.quantity} / ${MoneyValueService.formatCents(it.amountCents)}")
			}
		}.trimEnd()

		findViewById<TextView>(R.id.archiveDetailArticlesText).text = if (report.articleSummaries.isEmpty()) {
			"Artikel-/Verkaufsübersicht\nKeine Daten"
		} else {
			buildString {
				appendLine("Artikel-/Verkaufsübersicht")
				report.articleSummaries.forEach {
					appendLine("${it.articleName} (${it.category}/${it.taxCategory}): ${it.count} · ${MoneyValueService.formatCents(it.grossCents)}")
				}
			}.trimEnd()
		}

		findViewById<TextView>(R.id.archiveDetailEmployeesText).text = if (report.employeeSales.isEmpty()) {
			"Mitarbeiterverkäufe\nKeine Daten"
		} else {
			buildString {
				appendLine("Mitarbeiterverkäufe")
				report.employeeSales.forEach { appendLine("${it.articleName}: ${it.count}") }
			}.trimEnd()
		}

		findViewById<TextView>(R.id.archiveDetailOrdersText).text = if (report.orderSummaries.isEmpty()) {
			"Bestellungen\nKeine Daten"
		} else {
			buildString {
				appendLine("Bestellungen")
				report.orderSummaries.forEach { order ->
					appendLine("${order.articleName} ${order.sizeName}: ${MoneyValueService.formatCents(order.totalCents)}")
					if (order.detailsText.isNotBlank()) {
						order.detailsText.lines().forEach { appendLine("  - $it") }
					}
				}
			}.trimEnd()
		}
	}

	private fun generatePdfOnly() {
		val report = currentReport ?: return
		runCatching {
			ArchivedReportPdfExporter.export(this, report)
			Snackbar.make(findViewById(android.R.id.content), "PDF wurde erzeugt.", Snackbar.LENGTH_LONG).show()
		}.onFailure {
			Snackbar.make(findViewById(android.R.id.content), "PDF-Export fehlgeschlagen.", Snackbar.LENGTH_LONG).show()
		}
	}

	private fun exportPdfToLocalStorage() {
		val report = currentReport ?: return
		runCatching {
			val file = ArchivedReportPdfExporter.export(this, report)
			pendingPdfFile = file
			val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
				addCategory(Intent.CATEGORY_OPENABLE)
				type = "application/pdf"
				putExtra(Intent.EXTRA_TITLE, ArchivedReportPdfExporter.buildSuggestedFileName(report))
			}
			savePdfLauncher.launch(createIntent)
		}.onFailure {
			pendingPdfFile = null
			Snackbar.make(findViewById(android.R.id.content), "PDF konnte nicht vorbereitet werden.", Snackbar.LENGTH_LONG).show()
		}
	}

	private fun savePdfToUri(uri: Uri) {
		val file = pendingPdfFile ?: return
		runCatching {
			contentResolver.openOutputStream(uri)?.use { output ->
				file.inputStream().use { input -> input.copyTo(output) }
			} ?: throw IllegalStateException("Ausgabeziel konnte nicht geöffnet werden")
			file.delete()
			Snackbar.make(findViewById(android.R.id.content), "PDF wurde erfolgreich gespeichert.", Snackbar.LENGTH_LONG).show()
		}.onFailure {
			Snackbar.make(findViewById(android.R.id.content), "PDF konnte nicht gespeichert werden.", Snackbar.LENGTH_LONG).show()
		}.also {
			pendingPdfFile = null
		}
	}

	private fun sharePdf() {
		val report = currentReport ?: return
		runCatching {
			val file = ArchivedReportPdfExporter.export(this, report)
			val shareIntent = ArchivedReportPdfExporter.createShareIntent(this, file)
			startActivity(Intent.createChooser(shareIntent, "PDF teilen"))
		}.onFailure {
			Snackbar.make(findViewById(android.R.id.content), "PDF-Teilen fehlgeschlagen.", Snackbar.LENGTH_LONG).show()
		}
	}

	companion object {
		const val EXTRA_ARCHIVE_ID = "extra_archive_id"
	}
}
