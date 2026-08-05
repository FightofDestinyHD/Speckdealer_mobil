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
		val revenueNormal   = records.filter { !it.isEmployee }.sumOf { it.priceCents }
		val revenueEmployee = records.filter {  it.isEmployee }.size
		val financeText = buildString {
			appendLine("Umsatz (ohne Mitarbeiter): ${currencyFmt.format(revenueNormal / 100.0)}")
			appendLine("Mitarbeiterverkäufe (kostenlos): $revenueEmployee Stück")
		}
		findViewById<TextView>(R.id.reportFinanceText).text = financeText.trimEnd()

		// --- Weingläser & Weinflaschen ---
		val weinRecords = records.filter { it.category == CategoryType.WEIN.storageValue }
		val glass01 = weinRecords.count { it.servingType == "GLASS_01" }
		val glass02 = weinRecords.count { it.servingType == "GLASS_02" }
		val glassValue = glass01 * 0.5 + glass02 * 1.0
		val glassesText = buildString {
			appendLine("Glas 0,1l (halbwertig): $glass01 Stück")
			appendLine("Glas 0,2l (vollwertig): $glass02 Stück")
			append("Gesamtwertigkeit: $glassValue Einheiten")
		}
		findViewById<TextView>(R.id.reportGlassesText).text = glassesText

		// --- Leergut: direkte Flaschen + Flaschen aus Glasmengen (je 750ml) ---
		val allWeinNames = weinRecords.map { it.articleName }.distinct()
		val leergutLines = mutableListOf<String>()
		var leergutGesamt = 0

		allWeinNames.forEach { name ->
			val nameRecords = weinRecords.filter { it.articleName == name }
			val mlGlaeser = nameRecords.count { it.servingType == "GLASS_01" } * 100 +
							nameRecords.count { it.servingType == "GLASS_02" } * 200
			val flaschenAusGlaesern = mlGlaeser / 750
			val direktFlaschen = nameRecords.count { it.servingType == "BOTTLE" }
			val gesamt = flaschenAusGlaesern + direktFlaschen
			if (gesamt > 0) {
				leergutLines.add(buildString {
					append("$name: $gesamt Fl.")
					if (direktFlaschen > 0 && flaschenAusGlaesern > 0)
						append("  (direkt: $direktFlaschen, aus Gläsern: $flaschenAusGlaesern)")
					else if (direktFlaschen > 0)
						append("  (direkt verkauft)")
					else
						append("  (aus Gläsern: ${mlGlaeser}ml)")
				})
				leergutGesamt += gesamt
			}
		}

		val leergutText = if (leergutLines.isEmpty()) {
			"Kein Leergut."
		} else {
			buildString {
				leergutLines.forEach { appendLine(it) }
				append("─────────────────")
				appendLine()
				append("Gesamt Leergut: $leergutGesamt Flaschen")
			}
		}
		findViewById<TextView>(R.id.reportLeergutText).text = leergutText

		// --- Softgetränke nach Artikelname ---
		val softRecords = records.filter { it.category == CategoryType.SOFTGETRAENKE.storageValue }
		val softByName  = softRecords.groupBy { it.articleName }
		val softText = if (softByName.isEmpty()) {
			"Keine Softgetränke verkauft."
		} else {
			softByName.entries.joinToString("\n") { (name, list) ->
				val empCount     = list.count { it.isEmployee }
				val mitDepCnt    = list.count { !it.isEmployee && it.depositCents > 0 }
				val ohneDepCnt   = list.count { !it.isEmployee && it.depositCents == 0 }
				buildString {
					append("$name: ${list.size} Stück")
					if (mitDepCnt  > 0) append("  m.Pf: $mitDepCnt")
					if (ohneDepCnt > 0) append("  o.Pf: $ohneDepCnt")
					if (empCount   > 0) append("  MA: $empCount")
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
