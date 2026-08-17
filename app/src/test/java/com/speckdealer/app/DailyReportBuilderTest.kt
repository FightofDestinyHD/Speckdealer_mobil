package com.speckdealer.app

import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.DepositMovement
import com.speckdealer.app.data.DepositMovementType
import com.speckdealer.app.data.SaleRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
}
