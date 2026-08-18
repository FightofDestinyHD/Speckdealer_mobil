package com.speckdealer.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Persistiert alle Tages-Verkaufsdatensätze in SharedPreferences als JSON-Array.
 * Der Log kann per clearToday() zurückgesetzt werden (Tagesabschluss).
 */
class DailySalesStorage(context: Context, namespaceSuffix: String = "prod") {

	private val prefsName = if (namespaceSuffix == "dev") "speckdealer_daily_sales_dev" else "speckdealer_daily_sales"
	private val prefs: SharedPreferences =
		context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
	private val store = SafeJsonArrayPreferencesStore(
		prefs = prefs,
		key = KEY_RECORDS,
		lockName = "$prefsName:sale_records"
	)
	private val lock = Any()

	companion object {
		private const val KEY_RECORDS = "sale_records"
	}

	fun appendRecords(records: List<SaleRecord>) {
		if (records.isEmpty()) return
		synchronized(lock) {
			val (writeResult, _) = store.updateArray { currentArray ->
				val existing = loadFromArray(currentArray).toMutableList()
				records.forEach { incoming ->
					if (existing.none { sameRecord(it, incoming) }) {
						existing.add(incoming)
					}
				}
				val normalized = normalize(existing)
				toJsonArray(normalized) to normalized
			}
			if (!writeResult.success) {
				throw IllegalStateException(writeResult.errorMessage ?: "Tagesverkäufe konnten nicht gespeichert werden")
			}
		}
	}

	fun loadAll(): List<SaleRecord> = synchronized(lock) { loadAllLocked() }

	fun clearToday() {
		synchronized(lock) {
			val writeResult = store.writeArray(JSONArray())
			if (!writeResult.success) {
				throw IllegalStateException(writeResult.errorMessage ?: "Tagesverkäufe konnten nicht geleert werden")
			}
		}
	}

	private fun loadAllLocked(): List<SaleRecord> {
		val read = store.readArray()
		return loadFromArray(read.array)
	}

	private fun loadFromArray(arr: JSONArray): List<SaleRecord> {
		val parsed = (0 until arr.length()).mapNotNull {
			runCatching { SaleRecord.fromJson(arr.getJSONObject(it)) }.getOrNull()
		}
		return normalize(parsed)
	}

	private fun normalize(records: List<SaleRecord>): List<SaleRecord> {
		return records
			.distinctBy { dedupeKey(it) }
			.sortedBy { it.timestampMs }
	}

	private fun dedupeKey(record: SaleRecord): String {
		if (record.recordId.isNotBlank()) {
			return record.recordId
		}
		return listOf(
			record.transactionId,
			record.checkoutId,
			record.originOrderId,
			record.articleName,
			record.category,
			record.servingType,
			record.priceCents.toString(),
			record.depositCents.toString(),
			record.isEmployee.toString(),
			record.timestampMs.toString()
		).joinToString("|")
	}

	private fun sameRecord(a: SaleRecord, b: SaleRecord): Boolean {
		if (a.recordId.isNotBlank() && b.recordId.isNotBlank()) {
			return a.recordId == b.recordId
		}
		return dedupeKey(a) == dedupeKey(b)
	}

	private fun toJsonArray(records: List<SaleRecord>): JSONArray {
		val arr = JSONArray()
		records.forEach { arr.put(it.toJson()) }
		return arr
	}
}
