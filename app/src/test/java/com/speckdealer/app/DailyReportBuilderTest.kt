package com.speckdealer.app

import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.SaleRecord
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DailyReportBuilderTest {

	@Test
	fun buildsCorrectDailyReportSections() {
		val records = listOf(
			SaleRecord("Wein A", CategoryType.WEIN.storageValue, "BOTTLE", 1000, 0, false, transactionId = "t1", recordId = "r1", timestampMs = 1),
			SaleRecord("Wein A", CategoryType.WEIN.storageValue, "GLASS_01", 500, 100, false, transactionId = "t1", recordId = "r2", timestampMs = 2),
			SaleRecord("Cola", CategoryType.SOFTGETRAENKE.storageValue, "STANDARD", 300, 80, false, transactionId = "t2", recordId = "r3", timestampMs = 3),
			SaleRecord("Snack Teller", CategoryType.SNACKS.storageValue, "GROSS", 1200, 200, false, transactionId = "t3", recordId = "r4", timestampMs = 4),
			SaleRecord("Angebot X", CategoryType.ANGEBOT.storageValue, "KLEIN", 900, 200, false, transactionId = "t4", recordId = "r5", timestampMs = 5),
			SaleRecord("Angebot X (Flasche)", CategoryType.ANGEBOT.storageValue, "BOTTLE", 0, 0, false, transactionId = "t4", recordId = "r6", timestampMs = 6),
			SaleRecord("Cola", CategoryType.SOFTGETRAENKE.storageValue, "STANDARD", 0, 0, true, transactionId = "t5", recordId = "r7", timestampMs = 7)
		)

		val report = DailyReportBuilder.build(records, Locale.GERMANY)

		assertTrue(report.financeText.contains("Umsatz (ohne Mitarbeiter): 39,00"))
		assertTrue(report.financeText.contains("Mitarbeiterverkäufe (kostenlos): 1 Stück"))
		assertTrue(report.glassesText.contains("Glas 0,1l (halbwertig): 1 Stück"))
		assertTrue(report.leergutText.contains("Angebote (Weinflaschen): 1 Fl."))
		assertTrue(report.softdrinksText.contains("Cola: 2 Stück"))
		assertTrue(report.tellerText.contains("Großer Teller: 1 Stück"))
		assertTrue(report.tellerText.contains("Kleiner Teller: 1 Stück"))
		assertTrue(report.employeeText.contains("Cola: 1 Stück"))
	}
}
