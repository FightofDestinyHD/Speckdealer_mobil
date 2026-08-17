package com.speckdealer.app

import com.speckdealer.app.data.BeginJournalResult
import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.CheckoutJournalEntry
import com.speckdealer.app.data.CheckoutJournalStatus
import com.speckdealer.app.data.CheckoutJournalStorage
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
	val explicitOfferTaxCategory: TaxCategory? = null,
	val orderDraft: OrderDraftPayload? = null
)

data class CheckoutResult(
	val checkoutId: String,
	val transactionId: String,
	val saleCount: Int,
	val orderCount: Int,
	val alreadyPersisted: Boolean = false
)

class CheckoutService(
	private val appendSales: (List<SaleRecord>) -> Unit,
	private val appendOrders: (List<OrderRecord>) -> Unit,
	private val loadSalesByTransaction: (String) -> List<SaleRecord>,
	private val loadOrdersByTransaction: (String) -> List<OrderRecord>,
	private val beginJournal: (String) -> BeginJournalResult,
	private val markJournalCompleted: (String, List<String>, List<String>) -> Unit,
	private val markJournalFailed: (String, String) -> Unit,
	private val loadJournal: (String) -> CheckoutJournalEntry?
) {
	constructor(
		dailySalesStorage: DailySalesStorage,
		orderStorage: OrderStorage,
		journalStorage: CheckoutJournalStorage
	) : this(
		appendSales = { dailySalesStorage.appendRecords(it) },
		appendOrders = { orderStorage.addAll(it) },
		loadSalesByTransaction = { txId -> dailySalesStorage.loadAll().filter { it.transactionId == txId } },
		loadOrdersByTransaction = { txId -> orderStorage.loadAll().filter { it.transactionId == txId } },
		beginJournal = { txId -> journalStorage.beginIfMissing(txId) },
		markJournalCompleted = { txId, saleIds, orderIds -> journalStorage.markCompleted(txId, saleIds, orderIds) },
		markJournalFailed = { txId, error -> journalStorage.markFailed(txId, error) },
		loadJournal = { txId -> journalStorage.load(txId) }
	)

	constructor(
		appendSales: (List<SaleRecord>) -> Unit,
		appendOrders: (List<OrderRecord>) -> Unit
	) : this(
		appendSales = appendSales,
		appendOrders = appendOrders,
		loadSalesByTransaction = { emptyList() },
		loadOrdersByTransaction = { emptyList() },
		beginJournal = { BeginJournalResult(createdNew = true, existingEntry = null) },
		markJournalCompleted = { _, _, _ -> Unit },
		markJournalFailed = { _, _ -> Unit },
		loadJournal = { null }
	)

	fun checkout(
		drafts: List<SaleDraftEntry>,
		finalTotalCents: Long,
		transactionId: String = UUID.randomUUID().toString()
	): CheckoutResult {
		require(transactionId.isNotBlank()) { "transactionId darf nicht leer sein" }
		if (drafts.isEmpty()) {
			throw IllegalArgumentException("Checkout ohne Positionen ist nicht erlaubt")
		}

		val existingJournal = loadJournal(transactionId)
		if (existingJournal?.status == CheckoutJournalStatus.COMPLETED) {
			val existingSales = loadSalesByTransaction(transactionId)
			val existingOrders = loadOrdersByTransaction(transactionId)
			return CheckoutResult(
				checkoutId = existingSales.firstOrNull()?.checkoutId ?: transactionId,
				transactionId = transactionId,
				saleCount = existingSales.size,
				orderCount = existingOrders.size,
				alreadyPersisted = true
			)
		}

		val beginResult = beginJournal(transactionId)
		if (!beginResult.createdNew && beginResult.existingEntry?.status == CheckoutJournalStatus.PENDING) {
			val existingSales = loadSalesByTransaction(transactionId)
			val existingOrders = loadOrdersByTransaction(transactionId)
			if (existingSales.isNotEmpty() && existingOrders.size < drafts.count { it.orderDraft != null }) {
				markJournalFailed(transactionId, "Teilzustand erkannt: Sales vorhanden, Orders unvollständig")
			}
		}
		if (beginResult.existingEntry?.status == CheckoutJournalStatus.COMPLETED) {
			val existingSales = loadSalesByTransaction(transactionId)
			val existingOrders = loadOrdersByTransaction(transactionId)
			return CheckoutResult(
				checkoutId = existingSales.firstOrNull()?.checkoutId ?: transactionId,
				transactionId = transactionId,
				saleCount = existingSales.size,
				orderCount = existingOrders.size,
				alreadyPersisted = true
			)
		}

		val checkoutId = UUID.randomUUID().toString()
		val total = finalTotalCents.coerceAtLeast(0L)
		val totalDeposit = drafts.sumOf { it.depositCents.toLong().coerceAtLeast(0L) }
		if (total < totalDeposit) {
			throw IllegalArgumentException("Gesamtbetrag liegt unter dem Pfandanteil")
		}
		val taxableTotal = total - totalDeposit

		val sales = mutableListOf<SaleRecord>()
		val orders = mutableListOf<OrderRecord>()

		val weights = drafts.map { it.priceCents.toLong().coerceAtLeast(0L) }
		val adjustedPrices = MoneyValueService.allocateProportionally(weights, taxableTotal)

		drafts.forEachIndexed { index, draft ->
			val adjustedPrice = adjustedPrices[index].coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
			val saleRecordId = "$transactionId:sale:$index"
			val taxCategory = TaxPolicy.resolveTaxCategory(draft.category, draft.explicitOfferTaxCategory)
			val taxBreakdown = TaxCalculator.calculateFromGross(adjustedPrice, taxCategory)
			sales += SaleRecord(
				articleName = draft.articleName.ifBlank { draft.displayName },
				category = draft.category,
				servingType = draft.servingType,
				priceCents = adjustedPrice,
				depositCents = draft.depositCents,
				isEmployee = draft.isEmployee,
				taxCategory = taxCategory.name,
				taxRateBasisPoints = taxBreakdown.taxRateBasisPoints,
				netAmountCents = taxBreakdown.netAmountCents,
				taxAmountCents = taxBreakdown.taxAmountCents,
				grossAmountCents = taxBreakdown.grossAmountCents,
				checkoutId = checkoutId,
				transactionId = transactionId,
				recordId = saleRecordId
			)

			if (draft.createBottleHelperRecord && draft.category == CategoryType.ANGEBOT.storageValue) {
				sales += SaleRecord(
					articleName = "${draft.articleName.ifBlank { draft.displayName }} (Flasche)",
					category = CategoryType.ANGEBOT.storageValue,
					servingType = "BOTTLE",
					priceCents = 0,
					depositCents = 0,
					isEmployee = draft.isEmployee,
					taxCategory = TaxCategory.BEVERAGE.name,
					taxRateBasisPoints = 0,
					netAmountCents = 0,
					taxAmountCents = 0,
					grossAmountCents = 0,
					checkoutId = checkoutId,
					transactionId = transactionId,
					recordId = "$transactionId:sale:$index:bottle"
				)
			}

			draft.orderDraft?.let { order ->
				orders += OrderRecord(
					id = "$transactionId:order:$index",
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
					glaesser02 = order.glaesser02,
					transactionId = transactionId
				)
			}
		}

		val existingSales = loadSalesByTransaction(transactionId)
		val existingOrders = loadOrdersByTransaction(transactionId)
		if (existingSales.size > sales.size || existingOrders.size > orders.size) {
			markJournalFailed(transactionId, "Inkonsistente vorhandene Datensätze für transactionId=$transactionId")
			throw IllegalStateException("Inkonsistente Persistenzdaten entdeckt. Bitte Wiederherstellung prüfen.")
		}

		val salesToPersist = sales.filter { draft -> existingSales.none { it.recordId == draft.recordId } }
		val ordersToPersist = orders.filter { draft -> existingOrders.none { it.id == draft.id } }

		try {
			if (salesToPersist.isNotEmpty()) {
				appendSales(salesToPersist)
			}
			if (ordersToPersist.isNotEmpty()) {
				appendOrders(ordersToPersist)
			}
			markJournalCompleted(
				transactionId,
				sales.map { it.recordId },
				orders.map { it.id }
			)
		} catch (e: Exception) {
			markJournalFailed(transactionId, e.message ?: e.javaClass.simpleName)
			throw e
		}

		val finalSales = loadSalesByTransaction(transactionId).ifEmpty { sales }
		val finalOrders = loadOrdersByTransaction(transactionId).ifEmpty { orders }
		return CheckoutResult(
			checkoutId = checkoutId,
			transactionId = transactionId,
			saleCount = finalSales.size,
			orderCount = finalOrders.size
		)
	}
}
