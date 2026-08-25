package com.speckdealer.app

import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.DepositMovement
import com.speckdealer.app.data.DepositMovementType
import com.speckdealer.app.data.SaleRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.absoluteValue

class DailyReportBuilderTest {

	@Test
	fun buildsVatAndDepositSections_withStrictSeparation() {
		val records = listOf(
			SaleRecord(
				articleName = "Wein A",
				category = CategoryType.WEIN.storageValue,
				servingType = "BOTTLE",
				priceCents = 1190,
				depositCents = 200,
				isEmployee = false,
				taxCategory = TaxCategory.BEVERAGE.name,
				taxRateBasisPoints = 1900,
				netAmountCents = 1000,
				taxAmountCents = 190,
				grossAmountCents = 1190,
				transactionId = "t1",
				recordId = "r1",
				timestampMs = 1
			),
			SaleRecord(
				articleName = "Snack Teller",
				category = CategoryType.SNACKS.storageValue,
				servingType = "GROSS",
				priceCents = 1070,
				depositCents = 100,
				isEmployee = false,
				taxCategory = TaxCategory.FOOD.name,
				taxRateBasisPoints = 700,
				netAmountCents = 1000,
				taxAmountCents = 70,
				grossAmountCents = 1070,
				transactionId = "t2",
				recordId = "r2",
				timestampMs = 2
			),
			SaleRecord(
				articleName = "Cola",
				category = CategoryType.SOFTGETRAENKE.storageValue,
				servingType = "STANDARD",
				priceCents = 0,
				depositCents = 0,
				isEmployee = true,
				taxCategory = TaxCategory.BEVERAGE.name,
				taxRateBasisPoints = 1900,
				netAmountCents = 0,
				taxAmountCents = 0,
				grossAmountCents = 0,
				transactionId = "t3",
				recordId = "r3",
				timestampMs = 3
			)
		)
		val returns = listOf(
			DepositMovement(
				transactionId = "d1",
				depositType = "GLASS",
				displayName = "Glas",
				quantity = 2,
				unitAmountCents = 100,
				totalAmountCents = 200,
				movementType = DepositMovementType.RETURNED,
				timestampMs = 4
			)
		)

		val report = DailyReportBuilder.build(records, returns, Locale.GERMANY)

		assertTrue(report.summaryText.contains("Brutto-Umsatz ohne Pfand: 22,60"))
		assertTrue(report.summaryText.contains("Mehrwertsteuer gesamt (MwSt.): 2,60"))
		assertFalse(report.summaryText.contains("Umsatzsteuer"))
		assertTrue(report.beverageVatText.contains("MwSt. 19 % Getränke: 1,90"))
		assertTrue(report.foodVatText.contains("MwSt. 7 % Speisen: 0,70"))
		assertTrue(report.depositSummaryText.contains("Pfand erhalten: 3,00"))
		assertTrue(report.depositSummaryText.contains("Pfand zurückgegeben: 2,00"))
		assertTrue(report.depositSummaryText.contains("Pfand-Saldo: 1,00"))
		assertTrue(report.depositBreakdownText.contains("Flasche: 0,00"))
		assertTrue(report.depositBreakdownText.contains("Glas: 2,00"))
		assertTrue(report.depositBreakdownText.contains("Teller: 0,00"))
		assertTrue(report.employeeText.contains("Cola: 1 Stück"))
	}

	@Test
	fun pureDepositReturn_doesNotIncreaseNormalUmsatz_andAppearsInDepositSummary() {
		val records = listOf(
			SaleRecord(
				articleName = "Pfandrückgabe Glas",
				category = CategoryType.PFAND.storageValue,
				servingType = "RETURN",
				priceCents = 0,
				depositCents = -50,
				isEmployee = false,
				taxCategory = TaxCategory.DEPOSIT.name,
				taxRateBasisPoints = 0,
				netAmountCents = 0,
				taxAmountCents = 0,
				grossAmountCents = 0,
				transactionId = "tx-ret",
				recordId = "tx-ret:sale:0",
				timestampMs = 100
			)
		)
		val returns = listOf(
			DepositMovement(
				transactionId = "tx-ret",
				depositType = "GLASS",
				displayName = "Glas",
				quantity = 1,
				unitAmountCents = 50,
				totalAmountCents = 50,
				movementType = DepositMovementType.RETURNED,
				timestampMs = 101
			)
		)

		val report = DailyReportBuilder.build(records, returns, Locale.GERMANY)
		assertTrue(report.summaryText.contains("Brutto-Umsatz ohne Pfand: 0,00"))
		assertTrue(report.depositSummaryText.contains("Pfand erhalten: 0,00"))
		assertTrue(report.depositSummaryText.contains("Pfand zurückgegeben: 0,50"))
		assertTrue(report.depositSummaryText.contains("Pfand-Saldo: -0,50"))
		assertTrue(report.depositBreakdownText.contains("Glas: 0,50"))
	}

	@Test
	fun marksUnclearOffersInsteadOfSilentlyMisclassifyingThem() {
		val records = listOf(
			SaleRecord(
				articleName = "Angebot Spezial",
				category = CategoryType.ANGEBOT.storageValue,
				servingType = "STANDARD",
				priceCents = 1500,
				depositCents = 0,
				isEmployee = false,
				taxCategory = "",
				taxRateBasisPoints = 0,
				netAmountCents = 0,
				taxAmountCents = 0,
				grossAmountCents = 1500,
				transactionId = "t4",
				recordId = "r4",
				timestampMs = 5
			)
		)

		val report = DailyReportBuilder.build(records, emptyList(), Locale.GERMANY)

		assertTrue(report.summaryText.contains("Nicht zugeordnete Angebote"))
	}

	@Test
	fun depositSummary_usesOnlyPositiveReceived_andSingleBalanceSubtraction() {
		val records = listOf(
			sale("sale-pos", depositCents = 1000, grossCents = 1000),
			sale("sale-neg-return-style", depositCents = -500, grossCents = 0)
		)
		val returns = listOf(returnMovement("ret-1", "GLASS", "Glas", 500))

		val report = DailyReportBuilder.build(records, returns, Locale.GERMANY)

		assertTrue(report.depositSummaryText.contains("Pfand erhalten: 10,00"))
		assertTrue(report.depositSummaryText.contains("Pfand zurückgegeben: 5,00"))
		assertTrue(report.depositSummaryText.contains("Pfand-Saldo: 5,00"))
	}

	@Test
	fun depositSummary_onlyReturnedFiveEuro_resultsNegativeFiveBalance() {
		val report = DailyReportBuilder.build(
			records = listOf(sale("neg-only", depositCents = -500, grossCents = 0)),
			depositMovements = listOf(returnMovement("ret-only", "GLASS", "Glas", 500)),
			locale = Locale.GERMANY
		)

		assertTrue(report.depositSummaryText.contains("Pfand erhalten: 0,00"))
		assertTrue(report.depositSummaryText.contains("Pfand zurückgegeben: 5,00"))
		assertTrue(report.depositSummaryText.contains("Pfand-Saldo: -5,00"))
	}

	@Test
	fun depositSummary_onlyReceivedTenEuro_resultsPositiveTenBalance() {
		val report = DailyReportBuilder.build(
			records = listOf(sale("pos-only", depositCents = 1000, grossCents = 1000)),
			depositMovements = emptyList(),
			locale = Locale.GERMANY
		)

		assertTrue(report.depositSummaryText.contains("Pfand erhalten: 10,00"))
		assertTrue(report.depositSummaryText.contains("Pfand zurückgegeben: 0,00"))
		assertTrue(report.depositSummaryText.contains("Pfand-Saldo: 10,00"))
	}

	@Test
	fun depositSummary_receivedTenReturnedTen_resultsZeroBalance() {
		val report = DailyReportBuilder.build(
			records = listOf(sale("eq", depositCents = 1000, grossCents = 1000)),
			depositMovements = listOf(returnMovement("ret-eq", "BOTTLE", "Flasche", 1000)),
			locale = Locale.GERMANY
		)

		assertTrue(report.depositSummaryText.contains("Pfand-Saldo: 0,00"))
	}

	@Test
	fun depositSummary_receivedTenReturnedFifteen_resultsNegativeFiveBalance() {
		val report = DailyReportBuilder.build(
			records = listOf(sale("more-ret", depositCents = 1000, grossCents = 1000)),
			depositMovements = listOf(returnMovement("ret-more", "PLATE", "Teller", 1500)),
			locale = Locale.GERMANY
		)

		assertTrue(report.depositSummaryText.contains("Pfand erhalten: 10,00"))
		assertTrue(report.depositSummaryText.contains("Pfand zurückgegeben: 15,00"))
		assertTrue(report.depositSummaryText.contains("Pfand-Saldo: -5,00"))
	}

	@Test
	fun depositBreakdown_separatesBottleGlassPlateReturns() {
		val report = DailyReportBuilder.build(
			records = listOf(sale("mixed", depositCents = 0, grossCents = 0)),
			depositMovements = listOf(
				returnMovement("b", "BOTTLE", "Flasche", 200),
				returnMovement("g", "GLASS", "Glas", 300),
				returnMovement("p", "PLATE", "Teller", 400)
			),
			locale = Locale.GERMANY
		)

		assertTrue(report.depositBreakdownText.contains("Flasche: 2,00"))
		assertTrue(report.depositBreakdownText.contains("Glas: 3,00"))
		assertTrue(report.depositBreakdownText.contains("Teller: 4,00"))
	}

	@Test
	fun multipleNormalSalesWithDeposit_areAccumulatedCorrectly() {
		val report = DailyReportBuilder.build(
			records = listOf(
				sale("a", depositCents = 200, grossCents = 1000),
				sale("b", depositCents = 300, grossCents = 1000),
				sale("c", depositCents = 500, grossCents = 1000)
			),
			depositMovements = emptyList(),
			locale = Locale.GERMANY
		)

		assertTrue(report.depositSummaryText.contains("Pfand erhalten: 10,00"))
		assertTrue(report.depositSummaryText.contains("Pfand-Saldo: 10,00"))
	}

	@Test
	fun multiplePureDepositReturns_areAccumulatedWithoutRevenueImpact() {
		val records = listOf(
			sale("ret-a", category = CategoryType.PFAND.storageValue, taxCategory = TaxCategory.DEPOSIT.name, depositCents = -200, grossCents = 0),
			sale("ret-b", category = CategoryType.PFAND.storageValue, taxCategory = TaxCategory.DEPOSIT.name, depositCents = -300, grossCents = 0)
		)
		val returns = listOf(
			returnMovement("r1", "GLASS", "Glas", 200),
			returnMovement("r2", "BOTTLE", "Flasche", 300)
		)

		val report = DailyReportBuilder.build(records, returns, Locale.GERMANY)

		assertTrue(report.summaryText.contains("Brutto-Umsatz ohne Pfand: 0,00"))
		assertTrue(report.depositSummaryText.contains("Pfand erhalten: 0,00"))
		assertTrue(report.depositSummaryText.contains("Pfand zurückgegeben: 5,00"))
	}

	@Test
	fun mixedCart_saleAndReturn_keepsRevenueAndDepositSeparated() {
		val records = listOf(
			sale("sale", depositCents = 1000, grossCents = 2000),
			sale("return-line", category = CategoryType.PFAND.storageValue, taxCategory = TaxCategory.DEPOSIT.name, depositCents = -500, grossCents = 0)
		)
		val returns = listOf(returnMovement("mix-ret", "GLASS", "Glas", 500))

		val report = DailyReportBuilder.build(records, returns, Locale.GERMANY)

		assertTrue(report.summaryText.contains("Brutto-Umsatz ohne Pfand: 20,00"))
		assertTrue(report.depositSummaryText.contains("Pfand erhalten: 10,00"))
		assertTrue(report.depositSummaryText.contains("Pfand zurückgegeben: 5,00"))
		assertTrue(report.depositSummaryText.contains("Pfand-Saldo: 5,00"))
	}

	private fun sale(
		id: String,
		category: String = CategoryType.WEIN.storageValue,
		taxCategory: String = TaxCategory.BEVERAGE.name,
		depositCents: Int,
		grossCents: Int
	): SaleRecord = SaleRecord(
		articleName = "Artikel-$id",
		category = category,
		servingType = "STANDARD",
		priceCents = grossCents,
		depositCents = depositCents,
		isEmployee = false,
		taxCategory = taxCategory,
		taxRateBasisPoints = if (taxCategory == TaxCategory.FOOD.name) 700 else 1900,
		netAmountCents = grossCents,
		taxAmountCents = 0,
		grossAmountCents = grossCents,
		transactionId = "tx-$id",
		recordId = "r-$id",
		timestampMs = id.hashCode().toLong().absoluteValue + 1
	)

	private fun returnMovement(
		id: String,
		type: String,
		displayName: String,
		amountCents: Long
	): DepositMovement = DepositMovement(
		transactionId = "ret-$id",
		depositType = type,
		displayName = displayName,
		quantity = 1,
		unitAmountCents = amountCents,
		totalAmountCents = amountCents,
		movementType = DepositMovementType.RETURNED,
		timestampMs = id.hashCode().toLong().absoluteValue + 2
	)
}
