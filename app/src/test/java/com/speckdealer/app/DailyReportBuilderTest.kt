package com.speckdealer.app

import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.DepositMovement
import com.speckdealer.app.data.DepositMovementType
import com.speckdealer.app.data.SaleRecord
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DailyReportBuilderTest {

	@Test
	fun buildsCorrectDailyReportSections_withTaxAndDepositSeparation() {
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

		assertTrue(report.financeText.contains("Netto-Umsatz: 20,00"))
		assertTrue(report.financeText.contains("Umsatzsteuer: 2,60"))
		assertTrue(report.financeText.contains("Brutto-Umsatz ohne Pfand: 22,60"))
		assertTrue(report.financeText.contains("Pfand erhalten: 3,00"))
		assertTrue(report.financeText.contains("Pfand zurückgegeben: 2,00"))
		assertTrue(report.financeText.contains("Pfand-Saldo: 1,00"))
		assertTrue(report.financeText.contains("Getränke 19 %:"))
		assertTrue(report.financeText.contains("Speisen 7 %:"))
		assertTrue(report.employeeText.contains("Cola: 1 Stück"))
	}
}
