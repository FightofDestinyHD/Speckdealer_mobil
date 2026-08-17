package com.speckdealer.app

import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.DepositMovement
import com.speckdealer.app.data.DepositMovementType
import com.speckdealer.app.data.OrderRecord
import com.speckdealer.app.data.SaleRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyReportArchiveBuilderTest {

	@Test
	fun buildCompletionTransactionId_isStableForSameSnapshot() {
		val sales = listOf(sampleSale())
		val deposits = listOf(sampleDeposit())
		val orders = listOf(sampleOrder())

		val first = DailyReportArchiveBuilder.buildCompletionTransactionId("2026-08-18", sales, deposits, orders)
		val second = DailyReportArchiveBuilder.buildCompletionTransactionId("2026-08-18", sales, deposits, orders)

		assertEquals(first, second)
	}

	@Test
	fun buildArchivedReport_containsSeparatedTaxAndDepositValues() {
		val sales = listOf(
			sampleSale(),
			SaleRecord(
				articleName = "Speckbrot",
				category = CategoryType.SPECK.storageValue,
				servingType = "STANDARD",
				priceCents = 535,
				depositCents = 0,
				isEmployee = false,
				taxCategory = TaxCategory.FOOD.name,
				taxRateBasisPoints = 700,
				netAmountCents = 500,
				taxAmountCents = 35,
				grossAmountCents = 535,
				transactionId = "t2",
				recordId = "r2",
				timestampMs = 2
			)
		)
		val deposits = listOf(
			sampleDeposit(),
			DepositMovement(
				transactionId = "d2",
				depositType = "PLATE",
				displayName = "Teller",
				quantity = 1,
				unitAmountCents = 150,
				totalAmountCents = 150,
				movementType = DepositMovementType.RETURNED,
				timestampMs = 3
			)
		)
		val orders = listOf(sampleOrder())
		val txId = DailyReportArchiveBuilder.buildCompletionTransactionId("2026-08-18", sales, deposits, orders)

		val archived = DailyReportArchiveBuilder.buildArchivedReport(
			businessDate = "2026-08-18",
			archivedAt = 100L,
			completionTransactionId = txId,
			records = sales,
			depositMovements = deposits,
			orders = orders
		)

		assertEquals(2, archived.salesCount)
		assertEquals(1, archived.orderCount)
		assertEquals(1725L, archived.totalRevenueCents)
		assertEquals(1000L, archived.beverageNetCents)
		assertEquals(190L, archived.beverageVatCents)
		assertEquals(1190L, archived.beverageGrossCents)
		assertEquals(500L, archived.foodNetCents)
		assertEquals(35L, archived.foodVatCents)
		assertEquals(535L, archived.foodGrossCents)
		assertEquals(200L, archived.depositReceivedCents)
		assertEquals(350L, archived.depositReturnedCents)
		assertEquals(-150L, archived.depositBalanceCents)
		assertTrue(archived.depositSummaries.any { it.depositType == "GLASS" && it.amountCents == 200L })
		assertTrue(archived.depositSummaries.any { it.depositType == "PLATE" && it.amountCents == 150L })
	}

	private fun sampleSale(): SaleRecord = SaleRecord(
		articleName = "Wein",
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
	)

	private fun sampleDeposit(): DepositMovement = DepositMovement(
		transactionId = "d1",
		depositType = "GLASS",
		displayName = "Glas",
		quantity = 2,
		unitAmountCents = 100,
		totalAmountCents = 200,
		movementType = DepositMovementType.RETURNED,
		timestampMs = 2
	)

	private fun sampleOrder(): OrderRecord = OrderRecord(
		id = "o1",
		articleName = "Teller",
		sizeName = "Groß",
		priceCents = 1000,
		depositCents = 0,
		isEmployee = false,
		gurken = 1,
		tomaten = 1,
		zwiebeln = 1,
		oliven = 1,
		brezeln = 1,
		sonderwunsch = "",
		transactionId = "o-tx",
		timestampMs = 4
	)
}
