package com.speckdealer.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveFlowWiringTest {

	@Test
	fun archiveTileClick_opensArchivedReportsActivity() {
		val content = readProjectFile("app/src/main/java/com/speckdealer/app/MainActivity.kt")
		assertTrue(content.contains("findViewById<View?>(R.id.archiveTile)"))
		assertTrue(content.contains("archiveTile?.setOnClickListener"))
		assertTrue(content.contains("Intent(this, ArchivedReportsActivity::class.java)"))
	}

	@Test
	fun archiveTileExistsInPhoneAndTabletLayouts() {
		val phone = readProjectFile("app/src/main/res/layout/activity_main.xml")
		val tablet = readProjectFile("app/src/main/res/layout-sw600dp/activity_main.xml")
		assertTrue(phone.contains("@+id/archiveTile"))
		assertTrue(tablet.contains("@+id/archiveTile"))
		assertTrue(phone.contains("@string/tile_archive"))
		assertTrue(tablet.contains("@string/tile_archive"))
	}

	@Test
	fun archivedReportsActivity_isRegisteredInManifest() {
		val manifest = readProjectFile("app/src/main/AndroidManifest.xml")
		assertTrue(manifest.contains("android:name=\".ArchivedReportsActivity\""))
	}

	@Test
	fun archivedList_usesOnlyStoredArchiveDataAndNewestFirst() {
		val activity = readProjectFile("app/src/main/java/com/speckdealer/app/ArchivedReportsActivity.kt")
		val storage = readProjectFile("app/src/main/java/com/speckdealer/app/data/ArchivedDailyReportStorage.kt")
		assertTrue(activity.contains("val reports = archiveStorage.loadAll()"))
		assertTrue(storage.contains("sortedByDescending { it.archivedAt }"))
	}

	@Test
	fun archivedDetail_loadsSnapshotByArchiveId() {
		val detail = readProjectFile("app/src/main/java/com/speckdealer/app/ArchivedReportDetailActivity.kt")
		assertTrue(detail.contains("loadAll().firstOrNull { it.id == archiveId }"))
	}

	@Test
	fun pdfSave_usesCreateDocumentWithPdfMimeAndSuggestedTitle() {
		val detail = readProjectFile("app/src/main/java/com/speckdealer/app/ArchivedReportDetailActivity.kt")
		assertTrue(detail.contains("Intent.ACTION_CREATE_DOCUMENT"))
		assertTrue(detail.contains("type = \"application/pdf\""))
		assertTrue(detail.contains("Intent.EXTRA_TITLE"))
	}

	@Test
	fun pdfSaveAndShare_areSeparateActions() {
		val detail = readProjectFile("app/src/main/java/com/speckdealer/app/ArchivedReportDetailActivity.kt")
		assertTrue(detail.contains("R.id.archiveSavePdfButton"))
		assertTrue(detail.contains("R.id.archiveSharePdfButton"))
		assertTrue(detail.contains("exportPdfToLocalStorage()"))
		assertTrue(detail.contains("sharePdf()"))
	}

	@Test
	fun archiveDelete_usesConfirmationAndDeletesSingleRecord() {
		val detail = readProjectFile("app/src/main/java/com/speckdealer/app/ArchivedReportDetailActivity.kt")
		val storage = readProjectFile("app/src/main/java/com/speckdealer/app/data/ArchivedDailyReportStorage.kt")
		assertTrue(detail.contains("setTitle(\"Archivierten Tagesabschluss löschen?\")"))
		assertTrue(detail.contains("setMessage(\"Dieser archivierte Tagesabschluss wird dauerhaft gelöscht."))
		assertTrue(detail.contains("archiveStorage.deleteById(report.id)"))
		assertTrue(storage.contains("fun deleteById(reportId: String): Boolean"))
	}

	@Test
	fun pdfSave_cancelDoesNotTriggerErrorFlow() {
		val detail = readProjectFile("app/src/main/java/com/speckdealer/app/ArchivedReportDetailActivity.kt")
		assertTrue(detail.contains("if (result.resultCode != RESULT_OK || uri == null)"))
		assertTrue(detail.contains("return@registerForActivityResult"))
	}

	private fun readProjectFile(relativePath: String): String {
		val candidates = listOf(
			Paths.get(relativePath),
			Paths.get("..", relativePath),
			Paths.get("..", "..", relativePath)
		)
		val resolved = candidates.firstOrNull { it.exists() }
			?: throw IllegalStateException("Datei nicht gefunden: $relativePath")
		return Files.readAllLines(resolved).joinToString(separator = "\n")
	}
}
