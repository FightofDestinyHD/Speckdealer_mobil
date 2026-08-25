package com.speckdealer.app

import com.speckdealer.app.data.DepositMovement
import com.speckdealer.app.data.DepositMovementType
import com.speckdealer.app.data.SaleRecord
import java.text.NumberFormat
import java.util.Locale

data class DailyReportData(
	val summaryText: String,
	val beverageVatText: String,
	val foodVatText: String,
	val salesDetailsText: String,
	val employeeText: String,
	val depositSummaryText: String,
	val depositBreakdownText: String
)

object DailyReportBuilder {

	private enum class TaxBucket {
		BEVERAGE,
		FOOD,
		UNCLEAR_OFFER
	}

	fun build(
		records: List<SaleRecord>,
		depositMovements: List<DepositMovement> = emptyList(),
		locale: Locale = Locale.GERMANY
	): DailyReportData {
		val currencyFmt = NumberFormat.getCurrencyInstance(locale)
		fun formatEuro(cents: Long): String = currencyFmt.format(cents / 100.0)

		val taxableSales = records.filter { !it.isEmployee }
		val employeeSales = records.filter { it.isEmployee }

		val beverageSales = mutableListOf<SaleRecord>()
		val foodSales = mutableListOf<SaleRecord>()
		val unclearOfferSales = mutableListOf<SaleRecord>()

		taxableSales.forEach { record ->
			when (classifyTaxBucket(record)) {
				TaxBucket.BEVERAGE -> beverageSales.add(record)
				TaxBucket.FOOD -> foodSales.add(record)
				TaxBucket.UNCLEAR_OFFER -> unclearOfferSales.add(record)
				null -> Unit
			}
		}

		val beverageNet = beverageSales.sumOf { it.netAmountCents.toLong() }
		val beverageVat = beverageSales.sumOf { it.taxAmountCents.toLong() }
		val beverageGross = beverageSales.sumOf { it.grossAmountCents.toLong() }

		val foodNet = foodSales.sumOf { it.netAmountCents.toLong() }
		val foodVat = foodSales.sumOf { it.taxAmountCents.toLong() }
		val foodGross = foodSales.sumOf { it.grossAmountCents.toLong() }

		val totalNetKnown = beverageNet + foodNet
		val totalVatKnown = beverageVat + foodVat
		val totalGrossKnown = beverageGross + foodGross
		val totalGrossAllTaxable = taxableSales.sumOf { it.grossAmountCents.toLong() }
		val unclearOfferGross = unclearOfferSales.sumOf { it.grossAmountCents.toLong() }

		val depositReceived = taxableSales.sumOf { it.depositCents.toLong().coerceAtLeast(0L) }
		val returnedMovements = depositMovements.filter { it.movementType == DepositMovementType.RETURNED }
		val depositReturned = returnedMovements.sumOf { kotlin.math.abs(it.totalAmountCents) }
		val depositBalance = depositReceived - depositReturned

		val returnedBottle = returnedMovements
			.filter { isBottleType(it.depositType, it.displayName) }
			.sumOf { kotlin.math.abs(it.totalAmountCents) }
		val returnedGlass = returnedMovements
			.filter { isGlassType(it.depositType, it.displayName) }
			.sumOf { kotlin.math.abs(it.totalAmountCents) }
		val returnedPlate = returnedMovements
			.filter { isPlateType(it.depositType, it.displayName) }
			.sumOf { kotlin.math.abs(it.totalAmountCents) }
		val returnedOther = (depositReturned - returnedBottle - returnedGlass - returnedPlate).coerceAtLeast(0L)

		val summaryText = buildString {
			appendLine("Brutto-Umsatz ohne Pfand: ${formatEuro(totalGrossAllTaxable)}")
			appendLine("Netto-Umsatz (MwSt.-pflichtig): ${formatEuro(totalNetKnown)}")
			appendLine("Mehrwertsteuer gesamt (MwSt.): ${formatEuro(totalVatKnown)}")
			appendLine("Brutto (Netto + MwSt.): ${formatEuro(totalGrossKnown)}")
			if (unclearOfferSales.isNotEmpty()) {
				append("Nicht zugeordnete Angebote (Steuerkategorie unklar): ${unclearOfferSales.size} · ${formatEuro(unclearOfferGross)}")
			}
		}.trimEnd()

		val beverageVatText = buildString {
			appendLine("Netto-Umsatz Getränke: ${formatEuro(beverageNet)}")
			appendLine("MwSt. 19 % Getränke: ${formatEuro(beverageVat)}")
			append("Brutto-Umsatz Getränke: ${formatEuro(beverageGross)}")
		}.trimEnd()

		val foodVatText = buildString {
			appendLine("Netto-Umsatz Speisen: ${formatEuro(foodNet)}")
			appendLine("MwSt. 7 % Speisen: ${formatEuro(foodVat)}")
			append("Brutto-Umsatz Speisen: ${formatEuro(foodGross)}")
		}.trimEnd()

		val salesDetailsText = buildString {
			appendLine("Wein: ${taxableSales.count { it.category.equals("WEIN", ignoreCase = true) }}")
			appendLine("Softgetränke: ${taxableSales.count { it.category.equals("SOFTGETRAENKE", ignoreCase = true) }}")
			appendLine("Snacks: ${taxableSales.count { it.category.equals("SNACKS", ignoreCase = true) }}")
			appendLine("Käse: ${taxableSales.count { it.category.equals("KAESE", ignoreCase = true) }}")
			appendLine("Speck: ${taxableSales.count { it.category.equals("SPECK", ignoreCase = true) }}")
			append("Angebote: ${taxableSales.count { it.category.equals("ANGEBOT", ignoreCase = true) }}")
		}.trimEnd()

		val employeeByName = employeeSales.groupBy { it.articleName }
		val employeeText = if (employeeByName.isEmpty()) {
			"Keine Mitarbeiterverkäufe."
		} else {
			employeeByName.entries.joinToString("\n") { (name, list) -> "$name: ${list.size} Stück" }
		}

		val depositSummaryText = buildString {
			appendLine("Pfand erhalten: ${formatEuro(depositReceived)}")
			appendLine("Pfand zurückgegeben: ${formatEuro(depositReturned)}")
			append("Pfand-Saldo: ${formatEuro(depositBalance)}")
		}.trimEnd()

		val depositBreakdownText = buildString {
			appendLine("Flasche: ${formatEuro(returnedBottle)}")
			appendLine("Glas: ${formatEuro(returnedGlass)}")
			appendLine("Teller: ${formatEuro(returnedPlate)}")
			if (returnedOther > 0L) append("Sonstige: ${formatEuro(returnedOther)}")
		}.trimEnd()

		return DailyReportData(
			summaryText = summaryText,
			beverageVatText = beverageVatText,
			foodVatText = foodVatText,
			salesDetailsText = salesDetailsText,
			employeeText = employeeText,
			depositSummaryText = depositSummaryText,
			depositBreakdownText = depositBreakdownText
		)
	}

	private fun classifyTaxBucket(record: SaleRecord): TaxBucket? {
		val taxCategory = record.taxCategory.trim().uppercase()
		if (taxCategory == TaxCategory.BEVERAGE.name) return TaxBucket.BEVERAGE
		if (taxCategory == TaxCategory.FOOD.name) return TaxBucket.FOOD
		if (taxCategory == TaxCategory.DEPOSIT.name) return null

		val category = record.category.trim().uppercase()
		return when (category) {
			"WEIN", "SOFTGETRAENKE" -> TaxBucket.BEVERAGE
			"SNACKS", "KAESE", "SPECK" -> TaxBucket.FOOD
			"ANGEBOT" -> TaxBucket.UNCLEAR_OFFER
			else -> null
		}
	}

	private fun isBottleType(type: String, displayName: String): Boolean {
		val normalizedType = type.trim().uppercase()
		val normalizedName = displayName.trim().uppercase()
		return normalizedType == "BOTTLE" || normalizedType == "FLASCHE" || normalizedName == "FLASCHE"
	}

	private fun isGlassType(type: String, displayName: String): Boolean {
		val normalizedType = type.trim().uppercase()
		val normalizedName = displayName.trim().uppercase()
		return normalizedType == "GLASS" ||
			normalizedType == "GLAS" ||
			normalizedType == "GLASS_01" ||
			normalizedType == "GLASS_02" ||
			normalizedType == "GLAS_01" ||
			normalizedType == "GLAS_02" ||
			normalizedName == "GLAS"
	}

	private fun isPlateType(type: String, displayName: String): Boolean {
		val normalizedType = type.trim().uppercase()
		val normalizedName = displayName.trim().uppercase()
		return normalizedType == "PLATE" || normalizedType == "TELLER" || normalizedName == "TELLER"
	}
}
