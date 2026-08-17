package com.speckdealer.app.data

import org.json.JSONObject
import java.util.UUID

enum class DepositMovementType {
	RECEIVED,
	RETURNED;

	companion object {
		fun fromStorage(value: String?): DepositMovementType {
			return entries.firstOrNull { it.name == value } ?: RETURNED
		}
	}
}

data class DepositMovement(
	val id: String = UUID.randomUUID().toString(),
	val transactionId: String,
	val depositType: String,
	val quantity: Int,
	val unitAmountCents: Long,
	val totalAmountCents: Long,
	val movementType: DepositMovementType,
	val timestampMs: Long = System.currentTimeMillis()
) {
	init {
		require(transactionId.isNotBlank()) { "transactionId darf nicht leer sein" }
		require(depositType.isNotBlank()) { "depositType darf nicht leer sein" }
		require(quantity > 0) { "quantity muss > 0 sein" }
		require(unitAmountCents > 0L) { "unitAmountCents muss > 0 sein" }
		require(totalAmountCents > 0L) { "totalAmountCents muss > 0 sein" }
	}

	fun toJson(): JSONObject = JSONObject().apply {
		put("id", id)
		put("transactionId", transactionId)
		put("depositType", depositType)
		put("quantity", quantity)
		put("unitAmountCents", unitAmountCents)
		put("totalAmountCents", totalAmountCents)
		put("movementType", movementType.name)
		put("timestampMs", timestampMs)
	}

	companion object {
		fun fromJson(json: JSONObject): DepositMovement {
			val quantity = json.optInt("quantity", 0)
			val unitAmountCents = json.optLong("unitAmountCents", 0L)
			val totalAmountCents = json.optLong("totalAmountCents", 0L)
			return DepositMovement(
				id = json.optString("id", UUID.randomUUID().toString()),
				transactionId = json.optString("transactionId", ""),
				depositType = json.optString("depositType", ""),
				quantity = quantity,
				unitAmountCents = unitAmountCents,
				totalAmountCents = if (totalAmountCents > 0L) totalAmountCents else quantity.toLong() * unitAmountCents,
				movementType = DepositMovementType.fromStorage(json.optString("movementType", DepositMovementType.RETURNED.name)),
				timestampMs = json.optLong("timestampMs", System.currentTimeMillis())
			)
		}
	}
}
