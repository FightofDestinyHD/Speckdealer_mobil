package com.speckdealer.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class CheckoutJournalStatus {
	PENDING,
	COMPLETED,
	FAILED;

	companion object {
		fun fromStorage(value: String?): CheckoutJournalStatus {
			return entries.firstOrNull { it.name == value } ?: PENDING
		}
	}
}

data class CheckoutJournalEntry(
	val transactionId: String,
	val status: CheckoutJournalStatus,
	val saleRecordIds: List<String>,
	val orderRecordIds: List<String>,
	val errorMessage: String = "",
	val timestampMs: Long = System.currentTimeMillis()
) {
	fun toJson(): JSONObject = JSONObject().apply {
		put("transactionId", transactionId)
		put("status", status.name)
		put("saleRecordIds", JSONArray().apply { saleRecordIds.forEach { put(it) } })
		put("orderRecordIds", JSONArray().apply { orderRecordIds.forEach { put(it) } })
		put("errorMessage", errorMessage)
		put("timestampMs", timestampMs)
	}

	companion object {
		fun fromJson(json: JSONObject): CheckoutJournalEntry {
			return CheckoutJournalEntry(
				transactionId = json.optString("transactionId", ""),
				status = CheckoutJournalStatus.fromStorage(json.optString("status", CheckoutJournalStatus.PENDING.name)),
				saleRecordIds = json.optJSONArray("saleRecordIds")?.let { arr ->
					(0 until arr.length()).mapNotNull { idx -> arr.optString(idx, "").takeIf { it.isNotBlank() } }
				} ?: emptyList(),
				orderRecordIds = json.optJSONArray("orderRecordIds")?.let { arr ->
					(0 until arr.length()).mapNotNull { idx -> arr.optString(idx, "").takeIf { it.isNotBlank() } }
				} ?: emptyList(),
				errorMessage = json.optString("errorMessage", ""),
				timestampMs = json.optLong("timestampMs", System.currentTimeMillis())
			)
		}
	}
}

data class BeginJournalResult(
	val createdNew: Boolean,
	val existingEntry: CheckoutJournalEntry?
)

class CheckoutJournalStorage(context: Context, namespaceSuffix: String = "prod") {

	private val prefsName = if (namespaceSuffix == "dev") "speckdealer_checkout_journal_dev" else "speckdealer_checkout_journal"
	private val prefs: SharedPreferences =
		context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
	private val store = SafeJsonArrayPreferencesStore(
		prefs = prefs,
		key = KEY_RECORDS,
		lockName = "$prefsName:records"
	)
	private val lock = Any()

	companion object {
		private const val KEY_RECORDS = "checkout_journal_records"
	}

	fun beginIfMissing(transactionId: String): BeginJournalResult {
		require(transactionId.isNotBlank()) { "transactionId darf nicht leer sein" }
		synchronized(lock) {
			var existing: CheckoutJournalEntry? = null
			val (result, _) = store.updateArray { currentArray ->
				val list = loadFromArray(currentArray).toMutableList()
				existing = list.firstOrNull { it.transactionId == transactionId }
				if (existing == null) {
					list.add(
						CheckoutJournalEntry(
							transactionId = transactionId,
							status = CheckoutJournalStatus.PENDING,
							saleRecordIds = emptyList(),
							orderRecordIds = emptyList()
						)
					)
				}
				toJsonArray(normalize(list)) to Unit
			}
			if (!result.success) {
				throw IllegalStateException(result.errorMessage ?: "Journal konnte nicht gestartet werden")
			}
			return BeginJournalResult(createdNew = existing == null, existingEntry = existing)
		}
	}

	fun markCompleted(transactionId: String, saleRecordIds: List<String>, orderRecordIds: List<String>) {
		updateStatus(transactionId, CheckoutJournalStatus.COMPLETED, saleRecordIds, orderRecordIds, "")
	}

	fun markFailed(transactionId: String, errorMessage: String) {
		updateStatus(transactionId, CheckoutJournalStatus.FAILED, emptyList(), emptyList(), errorMessage)
	}

	fun load(transactionId: String): CheckoutJournalEntry? = synchronized(lock) {
		loadFromArray(store.readArray().array).firstOrNull { it.transactionId == transactionId }
	}

	private fun updateStatus(
		transactionId: String,
		status: CheckoutJournalStatus,
		saleRecordIds: List<String>,
		orderRecordIds: List<String>,
		errorMessage: String
	) {
		synchronized(lock) {
			val (result, _) = store.updateArray { currentArray ->
				val list = loadFromArray(currentArray).toMutableList()
				val index = list.indexOfFirst { it.transactionId == transactionId }
				if (index < 0) {
					list.add(
						CheckoutJournalEntry(
							transactionId = transactionId,
							status = status,
							saleRecordIds = saleRecordIds,
							orderRecordIds = orderRecordIds,
							errorMessage = errorMessage
						)
					)
				} else {
					list[index] = list[index].copy(
						status = status,
						saleRecordIds = if (saleRecordIds.isEmpty()) list[index].saleRecordIds else saleRecordIds,
						orderRecordIds = if (orderRecordIds.isEmpty()) list[index].orderRecordIds else orderRecordIds,
						errorMessage = errorMessage,
						timestampMs = System.currentTimeMillis()
					)
				}
				toJsonArray(normalize(list)) to Unit
			}
			if (!result.success) {
				throw IllegalStateException(result.errorMessage ?: "Journalstatus konnte nicht gespeichert werden")
			}
		}
	}

	private fun loadFromArray(arr: JSONArray): List<CheckoutJournalEntry> {
		val parsed = mutableListOf<CheckoutJournalEntry>()
		for (i in 0 until arr.length()) {
			val entry = runCatching { CheckoutJournalEntry.fromJson(arr.getJSONObject(i)) }.getOrNull()
			if (entry != null && entry.transactionId.isNotBlank()) {
				parsed.add(entry)
			}
		}
		return normalize(parsed)
	}

	private fun normalize(items: List<CheckoutJournalEntry>): List<CheckoutJournalEntry> {
		return items
			.distinctBy { it.transactionId }
			.sortedBy { it.timestampMs }
	}

	private fun toJsonArray(items: List<CheckoutJournalEntry>): JSONArray {
		val arr = JSONArray()
		items.forEach { arr.put(it.toJson()) }
		return arr
	}
}
