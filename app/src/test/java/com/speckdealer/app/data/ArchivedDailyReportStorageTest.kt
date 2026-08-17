package com.speckdealer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivedDailyReportStorageTest {

	@Test
	fun archivedDailyReport_keepsSnapshotValuesAndListsImmutableByValue() {
		val report = ArchivedDailyReport(
			id = "id-1",
			completionTransactionId = "tx-1",
			businessDate = "2026-08-18",
			archivedAt = 123L,
			totalRevenueCents = 1000,
			beverageNetCents = 500,
			beverageVatCents = 95,
			beverageGrossCents = 595,
			foodNetCents = 300,
			foodVatCents = 21,
			foodGrossCents = 321,
			depositReceivedCents = 120,
			depositReturnedCents = 20,
			depositBalanceCents = 100,
			salesCount = 4,
			orderCount = 2,
			employeeSales = listOf(ArchivedEmployeeSaleSummary("Cola", 1)),
			articleSummaries = listOf(ArchivedArticleSummary("Wein", "WEIN", "BEVERAGE", 2, 595)),
			depositSummaries = listOf(ArchivedDepositSummary("GLASS", "Glas", 1, 20)),
			orderSummaries = listOf(ArchivedOrderSummary("o1", "Teller", "Groß", 1000, false, "")),
			sourceSnapshotVersion = 1
		)

		assertEquals("tx-1", report.completionTransactionId)
		assertEquals("2026-08-18", report.businessDate)
		assertEquals(1000L, report.totalRevenueCents)
		assertEquals(100L, report.depositBalanceCents)
		assertEquals(1, report.depositSummaries.size)
		assertEquals("Glas", report.depositSummaries.first().displayName)
		assertTrue(report.articleSummaries.isNotEmpty())
		assertFalse(report.employeeSales.isEmpty())
	}
}
