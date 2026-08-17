package com.speckdealer.app.data

import org.json.JSONObject

/**
 * Repräsentiert einen einzelnen verkauften Posten im Tageslog.
 */
data class SaleRecord(
	val articleName: String,     // z.B. "Coca Cola", "Spätburgunder"
	val category: String,        // CategoryType.storageValue
	val servingType: String,     // "FLASCHE", "GLAS_01", "GLAS_02", "STANDARD"
	val priceCents: Int,         // Steuerpflichtiger Bruttopreis ohne Pfand
	val depositCents: Int,       // Pfandbetrag separat
	val isEmployee: Boolean,     // true = Mitarbeiterverkauf
	val taxCategory: String = "",
	val taxRateBasisPoints: Int = 0,
	val netAmountCents: Int = priceCents,
	val taxAmountCents: Int = 0,
	val grossAmountCents: Int = priceCents,
	val checkoutId: String = "", // Gruppiert Positionen eines Checkout-Vorgangs
	val originOrderId: String = "", // Referenz auf offene Bestellung (falls vorhanden)
	val transactionId: String = "",
	val recordId: String = "",
	val timestampMs: Long = System.currentTimeMillis()
) {
	fun toJson(): JSONObject = JSONObject().apply {
		put("articleName", articleName)
		put("category", category)
		put("servingType", servingType)
		put("priceCents", priceCents)
		put("depositCents", depositCents)
		put("isEmployee", isEmployee)
		put("taxCategory", taxCategory)
		put("taxRateBasisPoints", taxRateBasisPoints)
		put("netAmountCents", netAmountCents)
		put("taxAmountCents", taxAmountCents)
		put("grossAmountCents", grossAmountCents)
		put("checkoutId", checkoutId)
		put("originOrderId", originOrderId)
		put("transactionId", transactionId)
		put("recordId", recordId)
		put("timestampMs", timestampMs)
	}

	companion object {
		fun fromJson(json: JSONObject): SaleRecord {
			val checkoutId = json.optString("checkoutId", "")
			val transactionId = json.optString("transactionId", checkoutId)
			val recordId = json.optString("recordId", buildLegacyRecordId(json))
			val priceCents = json.optInt("priceCents", 0)
			val taxCategory = json.optString("taxCategory", "")
			val grossAmountCents = json.optInt("grossAmountCents", priceCents)
			return SaleRecord(
				articleName = json.optString("articleName", ""),
				category = json.optString("category", ""),
				servingType = json.optString("servingType", "STANDARD"),
				priceCents = priceCents,
				depositCents = json.optInt("depositCents", 0),
				isEmployee = json.optBoolean("isEmployee", false),
				taxCategory = taxCategory,
				taxRateBasisPoints = json.optInt("taxRateBasisPoints", 0),
				netAmountCents = json.optInt("netAmountCents", grossAmountCents),
				taxAmountCents = json.optInt("taxAmountCents", 0),
				grossAmountCents = grossAmountCents,
				checkoutId = checkoutId,
				originOrderId = json.optString("originOrderId", ""),
				transactionId = transactionId,
				recordId = recordId,
				timestampMs = json.optLong("timestampMs", System.currentTimeMillis())
			)
		}

		private fun buildLegacyRecordId(json: JSONObject): String {
			return listOf(
				json.optString("checkoutId", ""),
				json.optString("originOrderId", ""),
				json.optString("articleName", ""),
				json.optString("servingType", "STANDARD"),
				json.optInt("priceCents", 0).toString(),
				json.optInt("depositCents", 0).toString(),
				json.optLong("timestampMs", 0L).toString()
			).joinToString("|")
		}
	}
}
