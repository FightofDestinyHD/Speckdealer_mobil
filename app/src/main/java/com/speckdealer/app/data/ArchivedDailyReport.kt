package com.speckdealer.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ArchivedEmployeeSaleSummary(
	val articleName: String,
	val count: Int
) {
	fun toJson(): JSONObject = JSONObject().apply {
		put("articleName", articleName)
		put("count", count)
	}

	companion object {
		fun fromJson(json: JSONObject): ArchivedEmployeeSaleSummary {
			return ArchivedEmployeeSaleSummary(
				articleName = json.optString("articleName", ""),
				count = json.optInt("count", 0)
			)
		}
	}
}

data class ArchivedArticleSummary(
	val articleName: String,
	val category: String,
	val taxCategory: String,
	val count: Int,
	val grossCents: Long
) {
	fun toJson(): JSONObject = JSONObject().apply {
		put("articleName", articleName)
		put("category", category)
		put("taxCategory", taxCategory)
		put("count", count)
		put("grossCents", grossCents)
	}

	companion object {
		fun fromJson(json: JSONObject): ArchivedArticleSummary {
			return ArchivedArticleSummary(
				articleName = json.optString("articleName", ""),
				category = json.optString("category", ""),
				taxCategory = json.optString("taxCategory", ""),
				count = json.optInt("count", 0),
				grossCents = json.optLong("grossCents", 0L)
			)
		}
	}
}

data class ArchivedDepositSummary(
	val depositType: String,
	val displayName: String,
	val quantity: Int,
	val amountCents: Long
) {
	fun toJson(): JSONObject = JSONObject().apply {
		put("depositType", depositType)
		put("displayName", displayName)
		put("quantity", quantity)
		put("amountCents", amountCents)
	}

	companion object {
		fun fromJson(json: JSONObject): ArchivedDepositSummary {
			return ArchivedDepositSummary(
				depositType = json.optString("depositType", ""),
				displayName = json.optString("displayName", ""),
				quantity = json.optInt("quantity", 0),
				amountCents = json.optLong("amountCents", 0L)
			)
		}
	}
}

data class ArchivedOrderSummary(
	val id: String,
	val articleName: String,
	val sizeName: String,
	val totalCents: Long,
	val isEmployee: Boolean,
	val detailsText: String
) {
	fun toJson(): JSONObject = JSONObject().apply {
		put("id", id)
		put("articleName", articleName)
		put("sizeName", sizeName)
		put("totalCents", totalCents)
		put("isEmployee", isEmployee)
		put("detailsText", detailsText)
	}

	companion object {
		fun fromJson(json: JSONObject): ArchivedOrderSummary {
			return ArchivedOrderSummary(
				id = json.optString("id", ""),
				articleName = json.optString("articleName", ""),
				sizeName = json.optString("sizeName", ""),
				totalCents = json.optLong("totalCents", 0L),
				isEmployee = json.optBoolean("isEmployee", false),
				detailsText = json.optString("detailsText", "")
			)
		}
	}
}

data class ArchivedDailyReport(
	val id: String = UUID.randomUUID().toString(),
	val completionTransactionId: String,
	val businessDate: String,
	val archivedAt: Long,
	val totalRevenueCents: Long,
	val beverageNetCents: Long,
	val beverageVatCents: Long,
	val beverageGrossCents: Long,
	val foodNetCents: Long,
	val foodVatCents: Long,
	val foodGrossCents: Long,
	val depositReceivedCents: Long,
	val depositReturnedCents: Long,
	val depositBalanceCents: Long,
	val salesCount: Int,
	val orderCount: Int,
	val employeeSales: List<ArchivedEmployeeSaleSummary>,
	val articleSummaries: List<ArchivedArticleSummary>,
	val depositSummaries: List<ArchivedDepositSummary>,
	val orderSummaries: List<ArchivedOrderSummary>,
	val sourceSnapshotVersion: Int
) {
	init {
		require(completionTransactionId.isNotBlank()) { "completionTransactionId darf nicht leer sein" }
		require(businessDate.isNotBlank()) { "businessDate darf nicht leer sein" }
		require(sourceSnapshotVersion > 0) { "sourceSnapshotVersion muss > 0 sein" }
	}

	fun toJson(): JSONObject = JSONObject().apply {
		put("id", id)
		put("completionTransactionId", completionTransactionId)
		put("businessDate", businessDate)
		put("archivedAt", archivedAt)
		put("totalRevenueCents", totalRevenueCents)
		put("beverageNetCents", beverageNetCents)
		put("beverageVatCents", beverageVatCents)
		put("beverageGrossCents", beverageGrossCents)
		put("foodNetCents", foodNetCents)
		put("foodVatCents", foodVatCents)
		put("foodGrossCents", foodGrossCents)
		put("depositReceivedCents", depositReceivedCents)
		put("depositReturnedCents", depositReturnedCents)
		put("depositBalanceCents", depositBalanceCents)
		put("salesCount", salesCount)
		put("orderCount", orderCount)
		put("employeeSales", JSONArray().apply { employeeSales.forEach { put(it.toJson()) } })
		put("articleSummaries", JSONArray().apply { articleSummaries.forEach { put(it.toJson()) } })
		put("depositSummaries", JSONArray().apply { depositSummaries.forEach { put(it.toJson()) } })
		put("orderSummaries", JSONArray().apply { orderSummaries.forEach { put(it.toJson()) } })
		put("sourceSnapshotVersion", sourceSnapshotVersion)
	}

	companion object {
		fun fromJson(json: JSONObject): ArchivedDailyReport {
			return ArchivedDailyReport(
				id = json.optString("id", UUID.randomUUID().toString()),
				completionTransactionId = json.optString("completionTransactionId", ""),
				businessDate = json.optString("businessDate", ""),
				archivedAt = json.optLong("archivedAt", System.currentTimeMillis()),
				totalRevenueCents = json.optLong("totalRevenueCents", 0L),
				beverageNetCents = json.optLong("beverageNetCents", 0L),
				beverageVatCents = json.optLong("beverageVatCents", 0L),
				beverageGrossCents = json.optLong("beverageGrossCents", 0L),
				foodNetCents = json.optLong("foodNetCents", 0L),
				foodVatCents = json.optLong("foodVatCents", 0L),
				foodGrossCents = json.optLong("foodGrossCents", 0L),
				depositReceivedCents = json.optLong("depositReceivedCents", 0L),
				depositReturnedCents = json.optLong("depositReturnedCents", 0L),
				depositBalanceCents = json.optLong("depositBalanceCents", 0L),
				salesCount = json.optInt("salesCount", 0),
				orderCount = json.optInt("orderCount", 0),
				employeeSales = json.optJSONArray("employeeSales").toEmployeeSummaries(),
				articleSummaries = json.optJSONArray("articleSummaries").toArticleSummaries(),
				depositSummaries = json.optJSONArray("depositSummaries").toDepositSummaries(),
				orderSummaries = json.optJSONArray("orderSummaries").toOrderSummaries(),
				sourceSnapshotVersion = json.optInt("sourceSnapshotVersion", 1)
			)
		}

		private fun JSONArray?.toEmployeeSummaries(): List<ArchivedEmployeeSaleSummary> {
			if (this == null) return emptyList()
			val out = mutableListOf<ArchivedEmployeeSaleSummary>()
			for (i in 0 until length()) {
				optJSONObject(i)?.let { out.add(ArchivedEmployeeSaleSummary.fromJson(it)) }
			}
			return out
		}

		private fun JSONArray?.toArticleSummaries(): List<ArchivedArticleSummary> {
			if (this == null) return emptyList()
			val out = mutableListOf<ArchivedArticleSummary>()
			for (i in 0 until length()) {
				optJSONObject(i)?.let { out.add(ArchivedArticleSummary.fromJson(it)) }
			}
			return out
		}

		private fun JSONArray?.toDepositSummaries(): List<ArchivedDepositSummary> {
			if (this == null) return emptyList()
			val out = mutableListOf<ArchivedDepositSummary>()
			for (i in 0 until length()) {
				optJSONObject(i)?.let { out.add(ArchivedDepositSummary.fromJson(it)) }
			}
			return out
		}

		private fun JSONArray?.toOrderSummaries(): List<ArchivedOrderSummary> {
			if (this == null) return emptyList()
			val out = mutableListOf<ArchivedOrderSummary>()
			for (i in 0 until length()) {
				optJSONObject(i)?.let { out.add(ArchivedOrderSummary.fromJson(it)) }
			}
			return out
		}
	}
}
