package com.speckdealer.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class DepositMovementStorage(context: Context) {

	private val prefs: SharedPreferences =
		context.getSharedPreferences("speckdealer_deposit_movements", Context.MODE_PRIVATE)
	private val store = SafeJsonArrayPreferencesStore(
		prefs = prefs,
		key = KEY_RECORDS,
		lockName = "speckdealer_deposit_movements:records"
	)
	private val lock = Any()

	companion object {
		private const val KEY_RECORDS = "deposit_movements"
	}

	fun appendMovement(movement: DepositMovement) {
		synchronized(lock) {
			val (result, _) = store.updateArray { currentArray ->
				val existing = loadFromArray(currentArray).toMutableList()
				if (existing.none { it.id == movement.id }) {
					existing.add(movement)
				}
				val normalized = normalize(existing)
				toJsonArray(normalized) to Unit
			}
			if (!result.success) {
				throw IllegalStateException(result.errorMessage ?: "Pfandbewegung konnte nicht gespeichert werden")
			}
		}
	}

	fun appendMovements(movements: List<DepositMovement>) {
		if (movements.isEmpty()) return
		synchronized(lock) {
			val (result, _) = store.updateArray { currentArray ->
				val existing = loadFromArray(currentArray).toMutableList()
				movements.forEach { movement ->
					if (existing.none { it.id == movement.id }) {
						existing.add(movement)
					}
				}
				val normalized = normalize(existing)
				toJsonArray(normalized) to Unit
			}
			if (!result.success) {
				throw IllegalStateException(result.errorMessage ?: "Pfandbewegungen konnten nicht gespeichert werden")
			}
		}
	}

	fun loadAll(): List<DepositMovement> = synchronized(lock) {
		val read = store.readArray()
		loadFromArray(read.array)
	}

	fun clearToday() {
		synchronized(lock) {
			val result = store.writeArray(JSONArray())
			if (!result.success) {
				throw IllegalStateException(result.errorMessage ?: "Pfandbewegungen konnten nicht gelöscht werden")
			}
		}
	}

	private fun loadFromArray(arr: JSONArray): List<DepositMovement> {
		val parsed = mutableListOf<DepositMovement>()
		for (i in 0 until arr.length()) {
			val movement = runCatching { DepositMovement.fromJson(arr.getJSONObject(i)) }.getOrNull()
			if (movement != null) {
				parsed.add(movement)
			}
		}
		return normalize(parsed)
	}

	private fun normalize(items: List<DepositMovement>): List<DepositMovement> {
		return items
			.distinctBy { it.id }
			.sortedBy { it.timestampMs }
	}

	private fun toJsonArray(items: List<DepositMovement>): JSONArray {
		val arr = JSONArray()
		items.forEach { arr.put(it.toJson()) }
		return arr
	}
}
