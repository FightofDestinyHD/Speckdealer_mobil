package com.speckdealer.app.data

import org.json.JSONObject

enum class OrderStatus {
	OPEN,
	IN_PROGRESS,
	READY,
	COMPLETED,
	CANCELLED,
	PENDING_SYNC
}

data class OrderRecord(
	val id: String,
	val articleName: String,
	val sizeName: String,          // "Groß" / "Klein" / ""
	val priceCents: Int,
	val depositCents: Int,
	val isEmployee: Boolean,
	// Toppings (Anzahl, Standard = 1, Min = 0)
	val gurken: Int,
	val tomaten: Int,
	val zwiebeln: Int,
	val oliven: Int,
	val brezeln: Int,
	val sonderwunsch: String,
	// Angebot-Gläser (0 wenn kein Angebot)
	val glaesser01: Int = 0,
	val glaesser02: Int = 0,
	val transactionId: String = "",
	val timestampMs: Long = System.currentTimeMillis(),
	val createdAtUtcMs: Long = timestampMs,
	val updatedAtUtcMs: Long = timestampMs,
	val sourceDeviceId: String = "",
	val status: String = OrderStatus.OPEN.name,
	val syncVersion: Long = 1L,
	val isTestData: Boolean = false
) {
	/** Lesbare Zusammenfassung der Toppings/Sonderwünsche für die Bestellliste */
	fun buildDetailsText(): String = buildString {
		if (glaesser01 > 0) appendLine("$glaesser01× Glas 0,1l")
		if (glaesser02 > 0) appendLine("$glaesser02× Glas 0,2l")
		if (gurken   != 1) appendLine(if (gurken   == 0) "Keine Gurken"   else "$gurken× Gurken")
		if (tomaten  != 1) appendLine(if (tomaten  == 0) "Keine Tomaten"  else "$tomaten× Tomaten")
		if (zwiebeln != 1) appendLine(if (zwiebeln == 0) "Keine Zwiebeln" else "$zwiebeln× Zwiebeln")
		if (oliven   != 1) appendLine(if (oliven   == 0) "Keine Oliven"   else "$oliven× Oliven")
		if (brezeln  != 1) appendLine(if (brezeln  == 0) "Keine Brezeln"  else "$brezeln× Brezeln")
		if (sonderwunsch.isNotBlank()) appendLine("Sonderwunsch: $sonderwunsch")
	}.trimEnd()

	fun toJson(): JSONObject = JSONObject().apply {
		put("id", id)
		put("articleName", articleName)
		put("sizeName", sizeName)
		put("priceCents", priceCents)
		put("depositCents", depositCents)
		put("isEmployee", isEmployee)
		put("gurken", gurken)
		put("tomaten", tomaten)
		put("zwiebeln", zwiebeln)
		put("oliven", oliven)
		put("brezeln", brezeln)
		put("sonderwunsch", sonderwunsch)
		put("glaesser01", glaesser01)
		put("glaesser02", glaesser02)
		put("transactionId", transactionId)
		put("timestampMs", timestampMs)
		put("createdAtUtcMs", createdAtUtcMs)
		put("updatedAtUtcMs", updatedAtUtcMs)
		put("sourceDeviceId", sourceDeviceId)
		put("status", status)
		put("syncVersion", syncVersion)
		put("isTestData", isTestData)
	}

	companion object {
		fun fromJson(obj: JSONObject): OrderRecord {
			val now = System.currentTimeMillis()
			val timestamp = obj.optLong("timestampMs", now)
			return OrderRecord(
				id = obj.optString("id", now.toString()),
				articleName = obj.optString("articleName", ""),
				sizeName = obj.optString("sizeName", ""),
				priceCents = obj.optInt("priceCents", 0),
				depositCents = obj.optInt("depositCents", 0),
				isEmployee = obj.optBoolean("isEmployee", false),
				gurken = obj.optInt("gurken", 1),
				tomaten = obj.optInt("tomaten", 1),
				zwiebeln = obj.optInt("zwiebeln", 1),
				oliven = obj.optInt("oliven", 1),
				brezeln = obj.optInt("brezeln", 1),
				sonderwunsch = obj.optString("sonderwunsch", ""),
				glaesser01 = obj.optInt("glaesser01", 0),
				glaesser02 = obj.optInt("glaesser02", 0),
				transactionId = obj.optString("transactionId", ""),
				timestampMs = timestamp,
				createdAtUtcMs = obj.optLong("createdAtUtcMs", timestamp),
				updatedAtUtcMs = obj.optLong("updatedAtUtcMs", timestamp),
				sourceDeviceId = obj.optString("sourceDeviceId", ""),
				status = obj.optString("status", OrderStatus.OPEN.name),
				syncVersion = obj.optLong("syncVersion", 1L),
				isTestData = obj.optBoolean("isTestData", false)
			)
		}
	}

	fun withStatus(newStatus: OrderStatus, updatedAt: Long = System.currentTimeMillis()): OrderRecord {
		val nextVersion = (syncVersion + 1L).coerceAtLeast(1L)
		return copy(status = newStatus.name, updatedAtUtcMs = updatedAt, syncVersion = nextVersion)
	}
}
