package com.speckdealer.app.data

import org.json.JSONObject

/**
 * Repräsentiert einen einzelnen verkauften Posten im Tageslog.
 */
data class SaleRecord(
	val articleName: String,     // z.B. "Coca Cola", "Spätburgunder"
	val category: String,        // CategoryType.storageValue
	val servingType: String,     // "FLASCHE", "GLAS_01", "GLAS_02", "STANDARD"
	val priceCents: Int,         // Getränkepreis (0 bei Mitarbeiter)
	val depositCents: Int,       // Pfandbetrag (0 bei ohne Pfand oder Mitarbeiter)
	val isEmployee: Boolean,     // true = Mitarbeiterverkauf
	val checkoutId: String = "", // Gruppiert Positionen eines Checkout-Vorgangs
	val originOrderId: String = "", // Referenz auf offene Bestellung (falls vorhanden)
	val timestampMs: Long = System.currentTimeMillis()
) {
	fun toJson(): JSONObject = JSONObject().apply {
		put("articleName", articleName)
		put("category", category)
		put("servingType", servingType)
		put("priceCents", priceCents)
		put("depositCents", depositCents)
		put("isEmployee", isEmployee)
		put("checkoutId", checkoutId)
		put("originOrderId", originOrderId)
		put("timestampMs", timestampMs)
	}

	companion object {
		fun fromJson(json: JSONObject) = SaleRecord(
			articleName  = json.optString("articleName", ""),
			category     = json.optString("category", ""),
			servingType  = json.optString("servingType", "STANDARD"),
			priceCents   = json.optInt("priceCents", 0),
			depositCents = json.optInt("depositCents", 0),
			isEmployee   = json.optBoolean("isEmployee", false),
			checkoutId   = json.optString("checkoutId", ""),
			originOrderId = json.optString("originOrderId", ""),
			timestampMs  = json.optLong("timestampMs", System.currentTimeMillis())
		)
	}
}
