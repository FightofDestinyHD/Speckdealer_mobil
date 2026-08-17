package com.speckdealer.app

import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.DepositMovement
import com.speckdealer.app.data.DepositMovementType
import com.speckdealer.app.data.SaleRecord
import java.text.NumberFormat
import java.util.Locale

data class DailyReportData(
	val financeText: String,
	val glassesText: String,
	val leergutText: String,
	val softdrinksText: String,
	val tellerText: String,
	val employeeText: String
)

object DailyReportBuilder {
	fun build(
		records: List<SaleRecord>,
		depositMovements: List<DepositMovement> = emptyList(),
		locale: Locale = Locale.GERMANY
	): DailyReportData {
		val currencyFmt = NumberFormat.getCurrencyInstance(locale)

		val taxableSales = records.filter { !it.isEmployee }
		val beverageSales = taxableSales.filter { it.taxCategory == TaxCategory.BEVERAGE.name }
		val foodSales = taxableSales.filter { it.taxCategory == TaxCategory.FOOD.name }

		val beverageNet = beverageSales.sumOf { it.netAmountCents.toLong() }
		val beverageTax = beverageSales.sumOf { it.taxAmountCents.toLong() }
		val beverageGross = beverageSales.sumOf { it.grossAmountCents.toLong() }

		val foodNet = foodSales.sumOf { it.netAmountCents.toLong() }
		val foodTax = foodSales.sumOf { it.taxAmountCents.toLong() }
		val foodGross = foodSales.sumOf { it.grossAmountCents.toLong() }

		val depositReceived = taxableSales.sumOf { it.depositCents.toLong() }
		val depositReturned = depositMovements
			.filter { it.movementType == DepositMovementType.RETURNED }
			.sumOf { it.totalAmountCents }
		val depositBalance = depositReceived - depositReturned
		val netRevenueWithoutDeposit = beverageNet + foodNet
		val taxTotal = beverageTax + foodTax
		val grossRevenueWithoutDeposit = beverageGross + foodGross
		val revenueEmployee = records.count { it.isEmployee }

		val financeText = buildString {
			appendLine("Umsatz Waren/Getränke ohne Pfand: ${currencyFmt.format(grossRevenueWithoutDeposit / 100.0)}")
			appendLine("Netto-Umsatz: ${currencyFmt.format(netRevenueWithoutDeposit / 100.0)}")
			appendLine("Umsatzsteuer: ${currencyFmt.format(taxTotal / 100.0)}")
			appendLine("Brutto-Umsatz ohne Pfand: ${currencyFmt.format(grossRevenueWithoutDeposit / 100.0)}")
			appendLine("Pfand erhalten: ${currencyFmt.format(depositReceived / 100.0)}")
			appendLine("Pfand zurückgegeben: ${currencyFmt.format(depositReturned / 100.0)}")
			appendLine("Pfand-Saldo: ${currencyFmt.format(depositBalance / 100.0)}")
			appendLine("Auszahlungsbetrag Pfandrückgabe: ${currencyFmt.format(depositReturned / 100.0)}")
			appendLine("Mitarbeiterverkäufe (kostenlos): $revenueEmployee Stück")
			appendLine()
			appendLine("Getränke 19 %:")
			appendLine("  Netto: ${currencyFmt.format(beverageNet / 100.0)}")
			appendLine("  Steuer: ${currencyFmt.format(beverageTax / 100.0)}")
			appendLine("  Brutto: ${currencyFmt.format(beverageGross / 100.0)}")
			appendLine("Speisen 7 %:")
			appendLine("  Netto: ${currencyFmt.format(foodNet / 100.0)}")
			appendLine("  Steuer: ${currencyFmt.format(foodTax / 100.0)}")
			appendLine("  Brutto: ${currencyFmt.format(foodGross / 100.0)}")
		}.trimEnd()

		val weinRecords = records.filter { it.category == CategoryType.WEIN.storageValue }
		val glass01 = weinRecords.count { it.servingType == "GLASS_01" }
		val glass02 = weinRecords.count { it.servingType == "GLASS_02" }
		val glassValue = glass01 * 0.5 + glass02 * 1.0
		val glassesText = buildString {
			appendLine("Glas 0,1l (halbwertig): $glass01 Stück")
			appendLine("Glas 0,2l (vollwertig): $glass02 Stück")
			append("Gesamtwertigkeit: $glassValue Einheiten")
		}

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
					if (direktFlaschen > 0 && flaschenAusGlaesern > 0) {
						append("  (direkt: $direktFlaschen, aus Gläsern: $flaschenAusGlaesern)")
					} else if (direktFlaschen > 0) {
						append("  (direkt verkauft)")
					} else {
						append("  (aus Gläsern: ${mlGlaeser}ml)")
					}
				})
				leergutGesamt += gesamt
			}
		}

		val angebotFlaschen = records.filter {
			it.category == CategoryType.ANGEBOT.storageValue &&
				it.servingType == "BOTTLE" &&
				it.articleName.endsWith("(Flasche)")
		}
		if (angebotFlaschen.isNotEmpty()) {
			leergutLines.add("Angebote (Weinflaschen): ${angebotFlaschen.size} Fl.")
			leergutGesamt += angebotFlaschen.size
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

		val softRecords = records.filter { it.category == CategoryType.SOFTGETRAENKE.storageValue }
		val softByName = softRecords.groupBy { it.articleName }
		val softText = if (softByName.isEmpty()) {
			"Keine Softgetränke verkauft."
		} else {
			softByName.entries.joinToString("\n") { (name, list) ->
				val empCount = list.count { it.isEmployee }
				val mitDepCnt = list.count { !it.isEmployee && it.depositCents > 0 }
				val ohneDepCnt = list.count { !it.isEmployee && it.depositCents == 0 }
				buildString {
					append("$name: ${list.size} Stück")
					if (mitDepCnt > 0) append("  m.Pf: $mitDepCnt")
					if (ohneDepCnt > 0) append("  o.Pf: $ohneDepCnt")
					if (empCount > 0) append("  MA: $empCount")
				}
			}
		}

		val snackRecords = records.filter { it.category == CategoryType.SNACKS.storageValue }
		val angebotRecords = records.filter { it.category == CategoryType.ANGEBOT.storageValue }
		val tellerRecords = snackRecords + angebotRecords.filter { it.servingType != "BOTTLE" }
		val tellerGross = tellerRecords.count { it.servingType.uppercase() == "GROSS" }
		val tellerKlein = tellerRecords.count { it.servingType.uppercase() == "KLEIN" }
		val tellerSonstige = tellerRecords.count {
			it.servingType.uppercase() != "GROSS" && it.servingType.uppercase() != "KLEIN"
		}
		val tellerText = if (tellerRecords.isEmpty()) {
			"Keine Teller verkauft."
		} else {
			buildString {
				appendLine("Großer Teller: $tellerGross Stück")
				append("Kleiner Teller: $tellerKlein Stück")
				if (tellerSonstige > 0) append("\nSonstige (ohne Größe): $tellerSonstige Stück")
			}
		}

		val empRecords = records.filter { it.isEmployee }
		val empByName = empRecords.groupBy { it.articleName }
		val employeeText = if (empByName.isEmpty()) {
			"Keine Mitarbeiterverkäufe."
		} else {
			empByName.entries.joinToString("\n") { (name, list) ->
				"$name: ${list.size} Stück"
			}
		}

		return DailyReportData(
			financeText = financeText,
			glassesText = glassesText,
			leergutText = leergutText,
			softdrinksText = softText,
			tellerText = tellerText,
			employeeText = employeeText
		)
	}
}
