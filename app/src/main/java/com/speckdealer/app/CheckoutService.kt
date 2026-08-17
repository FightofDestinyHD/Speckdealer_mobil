package com.speckdealer.app

import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.DailySalesStorage
import com.speckdealer.app.data.OrderRecord
import com.speckdealer.app.data.OrderStorage
import com.speckdealer.app.data.SaleRecord
import java.util.UUID

data class OrderDraftPayload(
	val articleName: String,
	val sizeName: String,
	val basePriceCents: Int,
	val depositCents: Int,
	val isEmployee: Boolean,
	val gurken: Int = 1,
	val tomaten: Int = 1,
	val zwiebeln: Int = 1,
	val oliven: Int = 1,
	val brezeln: Int = 1,
	val sonderwunsch: String = "",
	val glaesser01: Int = 0,
	val glaesser02: Int = 0
)

data class SaleDraftEntry(
	val displayName: String,
	val totalCents: Int,
	val articleName: String,
	val category: String,
	val servingType: String = "STANDARD",
	val priceCents: Int,
	val depositCents: Int,
	val isEmployee: Boolean,
	val createBottleHelperRecord: Boolean = false,
	val orderDraft: OrderDraftPayload? = null
)

data class CheckoutResult(
	val checkoutId: String,
	val saleCount: Int,
	val orderCount: Int
)

class CheckoutService(
	private val dailySalesStorage: DailySalesStorage,
	private val orderStorage: OrderStorage
) {
	fun checkout(drafts: List<SaleDraftEntry>, finalTotalCents: Long): CheckoutResult {
		val checkoutId = UUID.randomUUID().toString()
		val total = finalTotalCents.coerceAtLeast(0L)

		val sales = mutableListOf<SaleRecord>()
		val orders = mutableListOf<OrderRecord>()

		val weights = drafts.map { it.priceCents.toLong().coerceAtLeast(0L) }
		val adjustedPrices = MoneyValueService.allocateProportionally(weights, total)

		drafts.forEachIndexed { index, draft ->
			val adjustedPrice = adjustedPrices[index].coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
			sales += SaleRecord(
				articleName = draft.articleName.ifBlank { draft.displayName },
				category = draft.category,
				servingType = draft.servingType,
				priceCents = adjustedPrice,
				depositCents = draft.depositCents,
				isEmployee = draft.isEmployee,
				checkoutId = checkoutId
			)

			if (draft.createBottleHelperRecord && draft.category == CategoryType.ANGEBOT.storageValue) {
				sales += SaleRecord(
					articleName = "${draft.articleName.ifBlank { draft.displayName }} (Flasche)",
					category = CategoryType.ANGEBOT.storageValue,
					servingType = "BOTTLE",
					priceCents = 0,
					depositCents = 0,
					isEmployee = draft.isEmployee,
					checkoutId = checkoutId
				)
			}

			draft.orderDraft?.let { order ->
				val orderId = UUID.randomUUID().toString()
				orders += OrderRecord(
					id = orderId,
					articleName = order.articleName,
					sizeName = order.sizeName,
					priceCents = order.basePriceCents,
					depositCents = order.depositCents,
					isEmployee = order.isEmployee,
					gurken = order.gurken,
					tomaten = order.tomaten,
					zwiebeln = order.zwiebeln,
					oliven = order.oliven,
					brezeln = order.brezeln,
					sonderwunsch = order.sonderwunsch,
					glaesser01 = order.glaesser01,
					glaesser02 = order.glaesser02
				)
			}
		}

		dailySalesStorage.appendRecords(sales)
		orderStorage.addAll(orders)

		return CheckoutResult(
			checkoutId = checkoutId,
			saleCount = sales.size,
			orderCount = orders.size
		)
	}
}
