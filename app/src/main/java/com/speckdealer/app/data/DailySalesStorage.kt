package com.speckdealer.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistiert alle Tages-Verkaufsdatensätze in SharedPreferences als JSON-Array.
 * Der Log kann per clearToday() zurückgesetzt werden (Tagesabschluss).
 */
class DailySalesStorage(context: Context) {

	private val prefs: SharedPreferences =
		context.getSharedPreferences("speckdealer_daily_sales", Context.MODE_PRIVATE)
	private val lock = Any()

	companion object {
		private const val KEY_RECORDS = "sale_records"
	}

	fun appendRecords(records: List<SaleRecord>) {
		if (records.isEmpty()) return
		synchronized(lock) {
			val existing = loadAllLocked().toMutableList()
			existing.addAll(records)
			saveAllLocked(existing)
		}
	}

	fun loadAll(): List<SaleRecord> = synchronized(lock) { loadAllLocked() }

	fun clearToday() {
		synchronized(lock) {
			prefs.edit().remove(KEY_RECORDS).commit()
		}
	}

	private fun loadAllLocked(): List<SaleRecord> {
		val json = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
		return try {
			val arr = JSONArray(json)
			(0 until arr.length()).map { SaleRecord.fromJson(arr.getJSONObject(it)) }
		} catch (e: Exception) {
			emptyList()
		}
	}

	private fun saveAllLocked(records: List<SaleRecord>) {
		val arr = JSONArray()
		records.forEach { arr.put(it.toJson()) }
		prefs.edit().putString(KEY_RECORDS, arr.toString()).commit()
	}
}
