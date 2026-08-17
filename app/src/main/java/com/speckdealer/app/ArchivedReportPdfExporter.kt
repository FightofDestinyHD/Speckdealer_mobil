package com.speckdealer.app

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.speckdealer.app.data.ArchivedDailyReport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ArchivedReportPdfExporter {

	fun export(context: Context, report: ArchivedDailyReport): File {
		val document = PdfDocument()
		val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
		val headingPaint = Paint().apply { textSize = 14f; isFakeBoldText = true }
		val textPaint = Paint().apply { textSize = 11f }

		val pageWidth = 595
		val pageHeight = 842
		var pageNumber = 1
		var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
		var canvas = page.canvas
		var y = 40f

		fun newPage() {
			canvas.drawText("Seite $pageNumber", 500f, 820f, textPaint)
			document.finishPage(page)
			pageNumber += 1
			page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
			canvas = page.canvas
			y = 40f
		}

		fun ensureSpace(lines: Int = 1) {
			if (y + (lines * 16f) > 790f) {
				newPage()
			}
		}

		fun drawLine(text: String, paint: Paint = textPaint) {
			ensureSpace()
			canvas.drawText(text, 40f, y, paint)
			y += 16f
		}

		canvas.drawText("Speckdealer", 40f, y, titlePaint)
		y += 22f
		drawLine("Archivierter Tagesabschluss", headingPaint)
		drawLine("Archiv-ID: ${report.id}")
		drawLine("Geschäftsdatum: ${report.businessDate}")
		val archivedAt = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date(report.archivedAt))
		drawLine("Archiviert am: $archivedAt")
		y += 8f

		drawLine("Umsatz und Mehrwertsteuer", headingPaint)
		drawLine("Gesamtumsatz (Brutto ohne Pfand): ${MoneyValueService.formatCents(report.totalRevenueCents)}")
		drawLine("Getränke Netto: ${MoneyValueService.formatCents(report.beverageNetCents)}")
		drawLine("Getränke MwSt. 19 %: ${MoneyValueService.formatCents(report.beverageVatCents)}")
		drawLine("Getränke Brutto: ${MoneyValueService.formatCents(report.beverageGrossCents)}")
		drawLine("Speisen Netto: ${MoneyValueService.formatCents(report.foodNetCents)}")
		drawLine("Speisen MwSt. 7 %: ${MoneyValueService.formatCents(report.foodVatCents)}")
		drawLine("Speisen Brutto: ${MoneyValueService.formatCents(report.foodGrossCents)}")
		y += 8f

		drawLine("Pfand", headingPaint)
		drawLine("Pfand erhalten: ${MoneyValueService.formatCents(report.depositReceivedCents)}")
		drawLine("Pfand zurückgegeben: ${MoneyValueService.formatCents(report.depositReturnedCents)}")
		drawLine("Pfand-Saldo: ${MoneyValueService.formatCents(report.depositBalanceCents)}")
		report.depositSummaries.forEach { summary ->
			drawLine("${summary.displayName}: ${summary.quantity} / ${MoneyValueService.formatCents(summary.amountCents)}")
		}
		y += 8f

		drawLine("Artikel-/Verkaufsübersicht", headingPaint)
		report.articleSummaries.forEach { item ->
			drawLine("${item.articleName} (${item.category}/${item.taxCategory}): ${item.count} · ${MoneyValueService.formatCents(item.grossCents)}")
		}
		y += 8f

		drawLine("Mitarbeiterverkäufe", headingPaint)
		if (report.employeeSales.isEmpty()) {
			drawLine("Keine Mitarbeiterverkäufe")
		} else {
			report.employeeSales.forEach { item ->
				drawLine("${item.articleName}: ${item.count}")
			}
		}
		y += 8f

		drawLine("Bestellungen", headingPaint)
		if (report.orderSummaries.isEmpty()) {
			drawLine("Keine offenen Bestellungen")
		} else {
			report.orderSummaries.forEach { order ->
				drawLine("${order.articleName} ${order.sizeName}: ${MoneyValueService.formatCents(order.totalCents)}")
				if (order.detailsText.isNotBlank()) {
					order.detailsText.lines().forEach { line -> drawLine("  - $line") }
				}
			}
		}

		canvas.drawText("Seite $pageNumber", 500f, 820f, textPaint)
		document.finishPage(page)

		val outputDir = File(context.filesDir, "archive-pdf").apply { mkdirs() }
		val safeDate = report.businessDate.replace(".", "-")
		val file = File(outputDir, "speckdealer-archiv-$safeDate-${report.id.take(8)}.pdf")
		file.outputStream().use { document.writeTo(it) }
		document.close()
		return file
	}

	fun createShareIntent(context: Context, file: File) = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
		type = "application/pdf"
		val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
		putExtra(android.content.Intent.EXTRA_STREAM, uri)
		addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
	}
}
