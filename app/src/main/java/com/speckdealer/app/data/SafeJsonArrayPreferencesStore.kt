package com.speckdealer.app.data

import android.content.SharedPreferences
import org.json.JSONArray
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SafeJsonArrayPreferencesStore(
	private val prefs: SharedPreferences,
	private val key: String,
	lockName: String
) {
	data class ReadResult(
		val array: JSONArray,
		val usedRecovery: Boolean,
		val recoveredFromBackup: Boolean
	)

	data class WriteResult(
		val success: Boolean,
		val transactionId: String,
		val errorMessage: String? = null
	)

	private val lock = lockFor(lockName)
	private val backupKey = "${key}_backup"
	private val corruptedPayloadKey = "${key}_corrupted_payload"
	private val lastErrorKey = "${key}_last_error"
	private val lastTransactionIdKey = "${key}_last_tx_id"
	private val lastTransactionTimeKey = "${key}_last_tx_time_ms"

	fun readArray(): ReadResult = synchronized(lock) {
		readArrayLocked()
	}

	fun writeArray(array: JSONArray, transactionId: String = UUID.randomUUID().toString()): WriteResult = synchronized(lock) {
		writeArrayLocked(array, transactionId)
	}

	fun <T> updateArray(update: (JSONArray) -> Pair<JSONArray, T>): Pair<WriteResult, T> = synchronized(lock) {
		val read = readArrayLocked()
		val (updatedArray, value) = update(read.array)
		val write = writeArrayLocked(updatedArray, UUID.randomUUID().toString())
		write to value
	}

	fun getCorruptedPayload(): String? = prefs.getString(corruptedPayloadKey, null)

	private fun readArrayLocked(): ReadResult {
		val raw = prefs.getString(key, null) ?: return ReadResult(JSONArray(), usedRecovery = false, recoveredFromBackup = false)
		return try {
			ReadResult(JSONArray(raw), usedRecovery = false, recoveredFromBackup = false)
		} catch (e: Exception) {
			recordCorruption(raw, e)
			val backup = prefs.getString(backupKey, null)
			if (backup.isNullOrBlank()) {
				ReadResult(JSONArray(), usedRecovery = true, recoveredFromBackup = false)
			} else {
				try {
					ReadResult(JSONArray(backup), usedRecovery = true, recoveredFromBackup = true)
				} catch (_: Exception) {
					ReadResult(JSONArray(), usedRecovery = true, recoveredFromBackup = false)
				}
			}
		}
	}

	private fun writeArrayLocked(array: JSONArray, transactionId: String): WriteResult {
		val payload = array.toString()
		val success = prefs.edit()
			.putString(key, payload)
			.putString(backupKey, payload)
			.putString(lastTransactionIdKey, transactionId)
			.putLong(lastTransactionTimeKey, System.currentTimeMillis())
			.remove(lastErrorKey)
			.commit()
		if (!success) {
			val message = "Commit fehlgeschlagen für key=$key txId=$transactionId"
			prefs.edit().putString(lastErrorKey, message).commit()
			return WriteResult(success = false, transactionId = transactionId, errorMessage = message)
		}
		return WriteResult(success = true, transactionId = transactionId)
	}

	private fun recordCorruption(raw: String, throwable: Throwable) {
		val error = "Beschädigtes JSON für key=$key: ${throwable.message ?: throwable.javaClass.simpleName}"
		prefs.edit()
			.putString(corruptedPayloadKey, raw)
			.putString(lastErrorKey, error)
			.commit()
	}

	companion object {
		private val locks = ConcurrentHashMap<String, Any>()

		private fun lockFor(name: String): Any = locks.getOrPut(name) { Any() }
	}
}
