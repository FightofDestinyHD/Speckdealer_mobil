package com.speckdealer.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class ArchivedDailyReportStorage(context: Context) {

	private val prefs: SharedPreferences =
		context.getSharedPreferences("speckdealer_archived_daily_reports", Context.MODE_PRIVATE)
	private val store = SafeJsonArrayPreferencesStore(
		prefs = prefs,
		key = KEY_ARCHIVE,
		lockName = "speckdealer_archived_daily_reports:archive"
	)
	private val lock = Any()

	companion object {
		private const val KEY_ARCHIVE = "archived_daily_reports"
	}

	fun saveIfAbsent(archivedReport: ArchivedDailyReport): Boolean {
		synchronized(lock) {
			val (result, inserted) = store.updateArray { currentArray ->
				val existing = loadFromArray(currentArray).toMutableList()
				if (existing.any { it.completionTransactionId == archivedReport.completionTransactionId || it.id == archivedReport.id }) {
					toJsonArray(existing) to false
				} else {
					existing.add(archivedReport)
					val normalized = normalize(existing)
					toJsonArray(normalized) to true
				}
			}
			if (!result.success) {
				throw IllegalStateException(result.errorMessage ?: "Archivdatensatz konnte nicht gespeichert werden")
			}
			return inserted
		}
	}

	fun loadAll(): List<ArchivedDailyReport> = synchronized(lock) {
		val read = store.readArray()
		loadFromArray(read.array)
	}

	fun findByCompletionTransactionId(transactionId: String): ArchivedDailyReport? {
		if (transactionId.isBlank()) return null
		return loadAll().firstOrNull { it.completionTransactionId == transactionId }
	}

	private fun loadFromArray(arr: JSONArray): List<ArchivedDailyReport> {
		val parsed = mutableListOf<ArchivedDailyReport>()
		for (i in 0 until arr.length()) {
			val report = runCatching { ArchivedDailyReport.fromJson(arr.getJSONObject(i)) }.getOrNull()
			if (report != null) {
				parsed.add(report)
			}
		}
		return normalize(parsed)
	}

	private fun normalize(items: List<ArchivedDailyReport>): List<ArchivedDailyReport> {
		return items
			.distinctBy { it.completionTransactionId.ifBlank { it.id } }
			.sortedByDescending { it.archivedAt }
	}

	private fun toJsonArray(items: List<ArchivedDailyReport>): JSONArray {
		val array = JSONArray()
		items.forEach { array.put(it.toJson()) }
		return array
	}
}
