package com.speckdealer.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.DailySalesStorage
import com.speckdealer.app.data.SaleRecord
import java.text.NumberFormat
import java.util.Locale

class DailyReportActivity : AppCompatActivity() {

	private lateinit var storage: DailySalesStorage
	private val currencyFmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_daily_report)

		storage = DailySalesStorage(this)

		findViewById<Button>(R.id.reportCloseButton).setOnClickListener { finish() }
		findViewById<Button>(R.id.reportResetButton).setOnClickListener { confirmReset() }

		buildReport()
	}

	private fun buildReport() {
		val records = storage.loadAll()

		// --- Finanzen ---
		val revenueNormal   = records.filter { !it.isEmployee }.sumOf { it.priceCents + it.depositCents }
		val revenueEmployee = records.filter {  it.isEmployee }.size
		val financeText = buildString {
			appendLine("Umsatz (ohne Mitarbeiter): ${currencyFmt.format(revenueNormal / 100.0)}")
			appendLine("Mitarbeiterverkäufe (kostenlos): $revenueEmployee Stück")
			val depositTotal = records.sumOf { it.depositCents }
			appendLine("Pfandeinnahmen gesamt: ${currencyFmt.format(depositTotal / 100.0)}")
		}
		findViewById<TextView>(R.id.reportFinanceText).text = financeText.trimEnd()

		// --- Weingläser ---
		val glass01 = records.count { it.category == CategoryType.WEIN.storageValue && it.servingType == "GLASS_01" }
		val glass02 = records.count { it.category == CategoryType.WEIN.storageValue && it.servingType == "GLASS_02" }
		// Wertigkeit: 0,1l = 0,5 Punkte, 0,2l = 1 Punkt
		val glassValue = glass01 * 0.5 + glass02 * 1.0
		val glassesText = buildString {
			appendLine("Glas 0,1l (halbwertig): $glass01 Stück")
			appendLine("Glas 0,2l (vollwertig): $glass02 Stück")
			appendLine("Gesamtwertigkeit: $glassValue Einheiten")
		}
		findViewById<TextView>(R.id.reportGlassesText).text = glassesText.trimEnd()

		// --- Softgetränke nach Artikelname ---
		val softRecords = records.filter { it.category == CategoryType.SOFTGETRAENKE.storageValue }
		val softByName  = softRecords.groupBy { it.articleName }
		val softText = if (softByName.isEmpty()) {
			"Keine Softgetränke verkauft."
		} else {
			softByName.entries.joinToString("\n") { (name, list) ->
				val empCount = list.count { it.isEmployee }
					buildString {
						append("$name: ${list.size} Stück")
						if (empCount > 0) append(" (davon $empCount MA)")
					}
			}
		}
		findViewById<TextView>(R.id.reportSoftdrinksText).text = softText

		// --- Mitarbeiterverkäufe ---
		val empRecords = records.filter { it.isEmployee }
		val empByName  = empRecords.groupBy { it.articleName }
		val empText = if (empByName.isEmpty()) {
			"Keine Mitarbeiterverkäufe."
		} else {
			empByName.entries.joinToString("\n") { (name, list) ->
				"$name: ${list.size} Stück"
			}
		}
		findViewById<TextView>(R.id.reportEmployeeText).text = empText
	}

	private fun confirmReset() {
		AlertDialog.Builder(this)
			.setTitle("Tag abschließen")
			.setMessage("Alle Tagesumsätze werden zurückgesetzt. Fortfahren?")
			.setPositiveButton("Zurücksetzen") { _, _ ->
				storage.clearToday()
				buildReport()
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}
}
