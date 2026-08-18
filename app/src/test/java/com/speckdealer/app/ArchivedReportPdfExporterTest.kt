package com.speckdealer.app

import com.speckdealer.app.data.ArchivedArticleSummary
import com.speckdealer.app.data.ArchivedDailyReport
import com.speckdealer.app.data.ArchivedDepositSummary
import com.speckdealer.app.data.ArchivedEmployeeSaleSummary
import com.speckdealer.app.data.ArchivedOrderSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchivedReportPdfExporterTest {

	@Test
	fun buildSuggestedFileName_containsBusinessDate() {
		val report = sampleReport(businessDate = "2026-08-18")
		val fileName = ArchivedReportPdfExporter.buildSuggestedFileName(report)
		assertEquals("Speckdealer_Tagesabschluss_2026-08-18.pdf", fileName)
	}

	private fun sampleReport(businessDate: String): ArchivedDailyReport {
		return ArchivedDailyReport(
			id = "id-1",
			completionTransactionId = "tx-1",
			businessDate = businessDate,
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
	}
}
