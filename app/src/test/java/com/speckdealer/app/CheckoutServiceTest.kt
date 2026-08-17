package com.speckdealer.app

import com.speckdealer.app.data.BeginJournalResult
import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.CheckoutJournalEntry
import com.speckdealer.app.data.CheckoutJournalStatus
import com.speckdealer.app.data.OrderRecord
import com.speckdealer.app.data.SaleRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckoutServiceTest {

	private val storedSales = mutableListOf<SaleRecord>()
	private val storedOrders = mutableListOf<OrderRecord>()
	private val journal = mutableMapOf<String, CheckoutJournalEntry>()
	private lateinit var checkoutService: CheckoutService
	private var failOrdersOnce = false

	@Before
	fun setup() {
		storedSales.clear()
		storedOrders.clear()
		journal.clear()
		failOrdersOnce = false
		checkoutService = CheckoutService(
			appendSales = { storedSales.addAll(it) },
			appendOrders = {
				if (failOrdersOnce) {
					failOrdersOnce = false
					throw IllegalStateException("order write failed")
				}
				storedOrders.addAll(it)
			},
			loadSalesByTransaction = { txId -> storedSales.filter { it.transactionId == txId } },
			loadOrdersByTransaction = { txId -> storedOrders.filter { it.transactionId == txId } },
			beginJournal = { txId ->
				val existing = journal[txId]
				if (existing == null) {
					journal[txId] = CheckoutJournalEntry(txId, CheckoutJournalStatus.PENDING, emptyList(), emptyList())
					BeginJournalResult(createdNew = true, existingEntry = null)
				} else {
					BeginJournalResult(createdNew = false, existingEntry = existing)
				}
			},
			markJournalCompleted = { txId, saleIds, orderIds ->
				journal[txId] = CheckoutJournalEntry(
					transactionId = txId,
					status = CheckoutJournalStatus.COMPLETED,
					saleRecordIds = saleIds,
					orderRecordIds = orderIds
				)
			},
			markJournalFailed = { txId, error ->
				val prev = journal[txId] ?: CheckoutJournalEntry(txId, CheckoutJournalStatus.PENDING, emptyList(), emptyList())
				journal[txId] = prev.copy(status = CheckoutJournalStatus.FAILED, errorMessage = error)
			},
			loadJournal = { txId -> journal[txId] }
		)
	}

	@Test
	fun snack_isSavedExactlyOnce() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Snack",
				totalCents = 1000,
				articleName = "Snack",
				category = CategoryType.SNACKS.storageValue,
				priceCents = 1000,
				depositCents = 200,
				isEmployee = false,
				orderDraft = OrderDraftPayload(
					articleName = "Snack",
					sizeName = "Groß",
					basePriceCents = 1000,
					depositCents = 200,
					isEmployee = false
				)
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1000, transactionId = "tx-1")

		assertEquals(1, storedSales.size)
		assertEquals(1, storedOrders.size)
	}

	@Test
	fun repeatedCheckout_sameTransactionId_persistsOnlyOnce() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Cola",
				totalCents = 300,
				articleName = "Cola",
				category = CategoryType.SOFTGETRAENKE.storageValue,
				priceCents = 300,
				depositCents = 80,
				isEmployee = false
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 300, transactionId = "tx-repeat")
		val second = checkoutService.checkout(drafts, finalTotalCents = 300, transactionId = "tx-repeat")

		assertEquals(1, storedSales.size)
		assertEquals(0, storedOrders.size)
		assertTrue(second.alreadyPersisted)
	}

	@Test
	fun partialFailure_ordersFail_retryDoesNotDuplicateSales() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Snack",
				totalCents = 1200,
				articleName = "Snack",
				category = CategoryType.SNACKS.storageValue,
				priceCents = 1200,
				depositCents = 0,
				isEmployee = false,
				orderDraft = OrderDraftPayload(
					articleName = "Snack",
					sizeName = "Groß",
					basePriceCents = 1200,
					depositCents = 0,
					isEmployee = false
				)
			)
		)

		failOrdersOnce = true
		runCatching { checkoutService.checkout(drafts, finalTotalCents = 1200, transactionId = "tx-fail") }
		assertEquals(1, storedSales.size)
		assertEquals(0, storedOrders.size)
		assertEquals(CheckoutJournalStatus.FAILED, journal["tx-fail"]?.status)

		checkoutService.checkout(drafts, finalTotalCents = 1200, transactionId = "tx-fail")
		assertEquals(1, storedSales.size)
		assertEquals(1, storedOrders.size)
		assertEquals(CheckoutJournalStatus.COMPLETED, journal["tx-fail"]?.status)
	}

	@Test
	fun angebot_requiresExplicitTaxCategory() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Angebot",
				totalCents = 1500,
				articleName = "Angebotsteller",
				category = CategoryType.ANGEBOT.storageValue,
				servingType = "GROSS",
				priceCents = 1500,
				depositCents = 300,
				isEmployee = false
			)
		)

		val result = runCatching { checkoutService.checkout(drafts, finalTotalCents = 1500, transactionId = "tx-offer") }
		assertTrue(result.isFailure)
	}

	@Test
	fun angebot_withExplicitTaxCategory_isStoredWithBottleHelperRecord() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Angebot",
				totalCents = 1500,
				articleName = "Angebotsteller",
				category = CategoryType.ANGEBOT.storageValue,
				servingType = "GROSS",
				priceCents = 1500,
				depositCents = 300,
				isEmployee = false,
				createBottleHelperRecord = true,
				explicitOfferTaxCategory = TaxCategory.FOOD
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1500, transactionId = "tx-offer-ok")
		assertEquals(2, storedSales.size)
		assertEquals(1, storedSales.count { it.servingType == "BOTTLE" })
	}

	@Test
	fun discountAndRounding_allocatesExactlyFinalTotal() {
		val drafts = listOf(
			SaleDraftEntry("A", 500, "A", CategoryType.SOFTGETRAENKE.storageValue, priceCents = 500, depositCents = 0, isEmployee = false),
			SaleDraftEntry("B", 500, "B", CategoryType.SOFTGETRAENKE.storageValue, priceCents = 500, depositCents = 0, isEmployee = false),
			SaleDraftEntry("C", 500, "C", CategoryType.SOFTGETRAENKE.storageValue, priceCents = 500, depositCents = 0, isEmployee = false)
		)

		checkoutService.checkout(drafts, finalTotalCents = 999, transactionId = "tx-round")

		val sum = storedSales.sumOf { it.priceCents.toLong() }
		assertEquals(999L, sum)
	}

	@Test
	fun checkoutTriggerGuard_preventsDoubleCheckoutTrigger() {
		val guard = CheckoutTriggerGuard()
		assertTrue(guard.tryStart())
		assertFalse(guard.tryStart())
		guard.finish()
		assertTrue(guard.tryStart())
	}
}
