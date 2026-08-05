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

	companion object {
		private const val KEY_RECORDS = "sale_records"
	}

	fun appendRecords(records: List<SaleRecord>) {
		val existing = loadAll().toMutableList()
		existing.addAll(records)
		saveAll(existing)
	}

	fun loadAll(): List<SaleRecord> {
		val json = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
		return try {
			val arr = JSONArray(json)
			(0 until arr.length()).map { SaleRecord.fromJson(arr.getJSONObject(it)) }
		} catch (e: Exception) {
			emptyList()
		}
	}

	fun clearToday() {
		prefs.edit().remove(KEY_RECORDS).apply()
	}

	private fun saveAll(records: List<SaleRecord>) {
		val arr = JSONArray()
		records.forEach { arr.put(it.toJson()) }
		prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
	}
}
