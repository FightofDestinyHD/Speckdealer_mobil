package com.speckdealer.app.data

import android.content.Context
import org.json.JSONArray

class OrderStorage(context: Context) {
	private val prefs = context.getSharedPreferences("speckdealer_orders", Context.MODE_PRIVATE)
	private val KEY = "open_orders"

	fun loadAll(): MutableList<OrderRecord> {
		val json = prefs.getString(KEY, "[]") ?: "[]"
		return try {
			val arr = JSONArray(json)
			(0 until arr.length()).mapNotNull {
				runCatching { OrderRecord.fromJson(arr.getJSONObject(it)) }.getOrNull()
			}.toMutableList()
		} catch (_: Exception) { mutableListOf() }
	}

	fun add(order: OrderRecord) {
		val list = loadAll()
		list.add(order)
		save(list)
	}

	fun remove(id: String) {
		val list = loadAll().filter { it.id != id }
		save(list)
	}

	fun clear() {
		prefs.edit().remove(KEY).apply()
	}

	private fun save(list: List<OrderRecord>) {
		val arr = JSONArray()
		list.forEach { arr.put(it.toJson()) }
		prefs.edit().putString(KEY, arr.toString()).apply()
	}
}
