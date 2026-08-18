package com.speckdealer.app.data

import android.content.Context
import org.json.JSONArray

class OrderStorage(context: Context, namespaceSuffix: String = "prod") {
	private val prefsName = if (namespaceSuffix == "dev") "speckdealer_orders_dev" else "speckdealer_orders"
	private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
	private val key = "open_orders"
	private val store = SafeJsonArrayPreferencesStore(
		prefs = prefs,
		key = key,
		lockName = "$prefsName:open_orders"
	)
	private val lock = Any()

	fun loadAll(): MutableList<OrderRecord> = synchronized(lock) { loadAllLocked().toMutableList() }

	fun add(order: OrderRecord) {
		addAll(listOf(order))
	}

	fun addAll(orders: List<OrderRecord>) {
		if (orders.isEmpty()) return
		synchronized(lock) {
			val (writeResult, _) = store.updateArray { currentArray ->
				val list = loadFromArray(currentArray).toMutableList()
				for (order in orders) {
					if (list.none { it.id == order.id }) {
						list.add(order)
					}
				}
				val normalized = normalize(list)
				toJsonArray(normalized) to normalized
			}
			if (!writeResult.success) {
				throw IllegalStateException(writeResult.errorMessage ?: "Bestellungen konnten nicht gespeichert werden")
			}
		}
	}

	fun remove(id: String) {
		synchronized(lock) {
			val (writeResult, _) = store.updateArray { currentArray ->
				val list = loadFromArray(currentArray).filter { it.id != id }
				val normalized = normalize(list)
				toJsonArray(normalized) to normalized
			}
			if (!writeResult.success) {
				throw IllegalStateException(writeResult.errorMessage ?: "Bestellung konnte nicht gelöscht werden")
			}
		}
	}

	fun clear() {
		synchronized(lock) {
			val writeResult = store.writeArray(JSONArray())
			if (!writeResult.success) {
				throw IllegalStateException(writeResult.errorMessage ?: "Bestellungen konnten nicht geleert werden")
			}
		}
	}

	private fun loadAllLocked(): List<OrderRecord> {
		val read = store.readArray()
		return loadFromArray(read.array)
	}

	private fun loadFromArray(arr: JSONArray): List<OrderRecord> {
		val parsed = (0 until arr.length()).mapNotNull {
			runCatching { OrderRecord.fromJson(arr.getJSONObject(it)) }.getOrNull()
		}
		return normalize(parsed)
	}

	private fun normalize(list: List<OrderRecord>): List<OrderRecord> {
		return list
			.distinctBy { it.id }
			.sortedBy { it.timestampMs }
	}

	private fun toJsonArray(list: List<OrderRecord>): JSONArray {
		val arr = JSONArray()
		list.forEach { arr.put(it.toJson()) }
		return arr
	}
}
