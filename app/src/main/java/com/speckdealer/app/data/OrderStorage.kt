package com.speckdealer.app.data

import android.content.Context
import org.json.JSONArray

class OrderStorage(context: Context) {
	private val prefs = context.getSharedPreferences("speckdealer_orders", Context.MODE_PRIVATE)
	private val KEY = "open_orders"
	private val lock = Any()

	fun loadAll(): MutableList<OrderRecord> = synchronized(lock) { loadAllLocked().toMutableList() }

	fun add(order: OrderRecord) {
		synchronized(lock) {
			val list = loadAllLocked().toMutableList()
			list.add(order)
			saveLocked(list)
		}
	}

	fun remove(id: String) {
		synchronized(lock) {
			val list = loadAllLocked().filter { it.id != id }
			saveLocked(list)
		}
	}

	fun clear() {
		synchronized(lock) {
			prefs.edit().remove(KEY).commit()
		}
	}

	private fun loadAllLocked(): List<OrderRecord> {
		val json = prefs.getString(KEY, "[]") ?: "[]"
		return try {
			val arr = JSONArray(json)
			(0 until arr.length()).mapNotNull {
				runCatching { OrderRecord.fromJson(arr.getJSONObject(it)) }.getOrNull()
			}
		} catch (_: Exception) { emptyList() }
	}

	private fun saveLocked(list: List<OrderRecord>) {
		val arr = JSONArray()
		list.forEach { arr.put(it.toJson()) }
		prefs.edit().putString(KEY, arr.toString()).commit()
	}
}
