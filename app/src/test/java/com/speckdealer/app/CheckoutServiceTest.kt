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
	fun pureDepositReturnGlass_allowsNegativeTotalAndDoesNotFail() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Pfandrückgabe Glas x1",
				totalCents = -50,
				articleName = "Pfandrückgabe Glas",
				category = CategoryType.PFAND.storageValue,
				servingType = "RETURN",
				priceCents = 0,
				depositCents = -50,
				isEmployee = false
			)
		)

		val result = checkoutService.checkout(drafts, finalTotalCents = -50, transactionId = "tx-return-glass")
		assertFalse(result.alreadyPersisted)
		assertEquals(1, storedSales.size)
		assertEquals(0, storedSales.first().priceCents)
	}

	@Test
	fun pureDepositReturnBottle_allowsNegativeTotalAndDoesNotFail() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Pfandrückgabe Flasche x1",
				totalCents = -25,
				articleName = "Pfandrückgabe Flasche",
				category = CategoryType.PFAND.storageValue,
				servingType = "RETURN",
				priceCents = 0,
				depositCents = -25,
				isEmployee = false
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = -25, transactionId = "tx-return-bottle")
		assertEquals(1, storedSales.size)
		assertEquals(CategoryType.PFAND.storageValue, storedSales.first().category)
	}

	@Test
	fun pureDepositReturnPlate_allowsNegativeTotalAndDoesNotFail() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Pfandrückgabe Teller x1",
				totalCents = -200,
				articleName = "Pfandrückgabe Teller",
				category = CategoryType.PFAND.storageValue,
				servingType = "RETURN",
				priceCents = 0,
				depositCents = -200,
				isEmployee = false
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = -200, transactionId = "tx-return-plate")
		assertEquals(1, storedSales.size)
		assertEquals(0, storedSales.first().grossAmountCents)
	}

	@Test
	fun pureDepositReturnMultipleItems_staysNegativeAndNonTaxable() {
		val drafts = listOf(
			SaleDraftEntry("Pfandrückgabe Glas x2", -100, "Pfandrückgabe Glas", CategoryType.PFAND.storageValue, "RETURN", 0, -100, false),
			SaleDraftEntry("Pfandrückgabe Flasche x1", -25, "Pfandrückgabe Flasche", CategoryType.PFAND.storageValue, "RETURN", 0, -25, false),
			SaleDraftEntry("Pfandrückgabe Teller x1", -200, "Pfandrückgabe Teller", CategoryType.PFAND.storageValue, "RETURN", 0, -200, false)
		)

		checkoutService.checkout(drafts, finalTotalCents = -325, transactionId = "tx-return-multi")
		assertEquals(3, storedSales.size)
		assertTrue(storedSales.all { it.priceCents == 0 })
	}

	@Test
	fun mixedSaleAndDepositReturn_keepsPositiveTaxablePrice() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Wein",
				totalCents = 400,
				articleName = "Wein",
				category = CategoryType.WEIN.storageValue,
				priceCents = 400,
				depositCents = 0,
				isEmployee = false
			),
			SaleDraftEntry(
				displayName = "Pfandrückgabe Glas x1",
				totalCents = -50,
				articleName = "Pfandrückgabe Glas",
				category = CategoryType.PFAND.storageValue,
				servingType = "RETURN",
				priceCents = 0,
				depositCents = -50,
				isEmployee = false
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 350, transactionId = "tx-mixed")
		assertEquals(2, storedSales.size)
		assertTrue(storedSales.any { it.category == CategoryType.WEIN.storageValue && it.priceCents > 0 })
	}

	@Test
	fun angebot_withDepositAndSonderwunsch_persistsOrderPayload() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Angebot Teller",
				totalCents = 1600,
				articleName = "Angebot Teller",
				category = CategoryType.ANGEBOT.storageValue,
				servingType = "GROSS",
				priceCents = 1500,
				depositCents = 100,
				isEmployee = false,
				createBottleHelperRecord = true,
				explicitOfferTaxCategory = TaxCategory.FOOD,
				orderDraft = OrderDraftPayload(
					articleName = "Angebot Teller",
					sizeName = "Groß",
					basePriceCents = 1500,
					depositCents = 100,
					isEmployee = false,
					gurken = 2,
					tomaten = 1,
					zwiebeln = 1,
					oliven = 0,
					brezeln = 1,
					sonderwunsch = "ohne Salz",
					glaesser01 = 2,
					glaesser02 = 1
				)
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1600, transactionId = "tx-offer-payload")

		assertEquals(1, storedOrders.size)
		assertEquals("ohne Salz", storedOrders.first().sonderwunsch)
		assertEquals(100, storedOrders.first().depositCents)
		assertEquals(2, storedOrders.first().glaesser01)
		assertEquals(1, storedOrders.first().glaesser02)
	}

	@Test
	fun angebot_withZeroGlasses_persistsZeroGlassCounts() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Angebot 0 Gläser",
				totalCents = 1500,
				articleName = "Angebot 0 Gläser",
				category = CategoryType.ANGEBOT.storageValue,
				servingType = "STANDARD",
				priceCents = 1500,
				depositCents = 0,
				isEmployee = false,
				explicitOfferTaxCategory = TaxCategory.FOOD,
				orderDraft = OrderDraftPayload(
					articleName = "Angebot 0 Gläser",
					sizeName = "",
					basePriceCents = 1500,
					depositCents = 0,
					isEmployee = false,
					glaesser01 = 0,
					glaesser02 = 0
				)
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1500, transactionId = "tx-offer-zero-glass")
		assertEquals(0, storedOrders.first().glaesser01)
		assertEquals(0, storedOrders.first().glaesser02)
	}

	@Test
	fun angebot_withOnlyGlass01_persistsGlassCounts() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Angebot Glas01",
				totalCents = 1700,
				articleName = "Angebot Glas01",
				category = CategoryType.ANGEBOT.storageValue,
				servingType = "STANDARD",
				priceCents = 1500,
				depositCents = 200,
				isEmployee = false,
				explicitOfferTaxCategory = TaxCategory.FOOD,
				orderDraft = OrderDraftPayload(
					articleName = "Angebot Glas01",
					sizeName = "",
					basePriceCents = 1500,
					depositCents = 200,
					isEmployee = false,
					glaesser01 = 4,
					glaesser02 = 0
				)
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1700, transactionId = "tx-offer-g01")
		assertEquals(4, storedOrders.first().glaesser01)
		assertEquals(0, storedOrders.first().glaesser02)
	}

	@Test
	fun angebot_withOnlyGlass02_persistsGlassCounts() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Angebot Glas02",
				totalCents = 1650,
				articleName = "Angebot Glas02",
				category = CategoryType.ANGEBOT.storageValue,
				servingType = "STANDARD",
				priceCents = 1500,
				depositCents = 150,
				isEmployee = false,
				explicitOfferTaxCategory = TaxCategory.FOOD,
				orderDraft = OrderDraftPayload(
					articleName = "Angebot Glas02",
					sizeName = "",
					basePriceCents = 1500,
					depositCents = 150,
					isEmployee = false,
					glaesser01 = 0,
					glaesser02 = 3
				)
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1650, transactionId = "tx-offer-g02")
		assertEquals(0, storedOrders.first().glaesser01)
		assertEquals(3, storedOrders.first().glaesser02)
	}

	@Test
	fun angebot_withMixedGlasses_persistsGlassCounts() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Angebot Mix Gläser",
				totalCents = 1750,
				articleName = "Angebot Mix Gläser",
				category = CategoryType.ANGEBOT.storageValue,
				servingType = "STANDARD",
				priceCents = 1500,
				depositCents = 250,
				isEmployee = false,
				explicitOfferTaxCategory = TaxCategory.FOOD,
				orderDraft = OrderDraftPayload(
					articleName = "Angebot Mix Gläser",
					sizeName = "",
					basePriceCents = 1500,
					depositCents = 250,
					isEmployee = false,
					glaesser01 = 3,
					glaesser02 = 2
				)
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1750, transactionId = "tx-offer-mixed-glass")
		assertEquals(3, storedOrders.first().glaesser01)
		assertEquals(2, storedOrders.first().glaesser02)
	}

	@Test
	fun angebot_withDepositAndSonderwunschAndGlasses_persistsCompletePayload() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Angebot komplett",
				totalCents = 1800,
				articleName = "Angebot komplett",
				category = CategoryType.ANGEBOT.storageValue,
				servingType = "KLEIN",
				priceCents = 1500,
				depositCents = 300,
				isEmployee = false,
				explicitOfferTaxCategory = TaxCategory.FOOD,
				orderDraft = OrderDraftPayload(
					articleName = "Angebot komplett",
					sizeName = "Klein",
					basePriceCents = 1500,
					depositCents = 300,
					isEmployee = false,
					gurken = 2,
					tomaten = 2,
					zwiebeln = 1,
					oliven = 1,
					brezeln = 0,
					sonderwunsch = "extra scharf",
					glaesser01 = 1,
					glaesser02 = 2
				)
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1800, transactionId = "tx-offer-complete")
		assertEquals("extra scharf", storedOrders.first().sonderwunsch)
		assertEquals(1, storedOrders.first().glaesser01)
		assertEquals(2, storedOrders.first().glaesser02)
	}

	@Test
	fun angebot_withoutDeposit_persistsZeroDeposit() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Angebot ohne Pfand",
				totalCents = 1500,
				articleName = "Angebot ohne Pfand",
				category = CategoryType.ANGEBOT.storageValue,
				servingType = "KLEIN",
				priceCents = 1500,
				depositCents = 0,
				isEmployee = false,
				explicitOfferTaxCategory = TaxCategory.FOOD,
				orderDraft = OrderDraftPayload(
					articleName = "Angebot ohne Pfand",
					sizeName = "Klein",
					basePriceCents = 1500,
					depositCents = 0,
					isEmployee = false
				)
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1500, transactionId = "tx-offer-no-deposit")
		assertEquals(0, storedSales.first().depositCents)
		assertEquals(0, storedOrders.first().depositCents)
	}

	@Test
	fun snack_withoutDeposit_persistsZeroDeposit() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Snack ohne Pfand",
				totalCents = 1000,
				articleName = "Snack ohne Pfand",
				category = CategoryType.SNACKS.storageValue,
				servingType = "KLEIN",
				priceCents = 1000,
				depositCents = 0,
				isEmployee = false,
				orderDraft = OrderDraftPayload(
					articleName = "Snack ohne Pfand",
					sizeName = "Klein",
					basePriceCents = 1000,
					depositCents = 0,
					isEmployee = false
				)
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1000, transactionId = "tx-snack-no-deposit")
		assertEquals(0, storedSales.first().depositCents)
	}

	@Test
	fun snack_withPlateDeposit_persistsDepositAndDoesNotBecomeTaxableRevenue() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Snack Teller",
				totalCents = 1200,
				articleName = "Snack Teller",
				category = CategoryType.SNACKS.storageValue,
				servingType = "GROSS",
				priceCents = 1000,
				depositCents = 200,
				isEmployee = false,
				orderDraft = OrderDraftPayload(
					articleName = "Snack Teller",
					sizeName = "Groß",
					basePriceCents = 1000,
					depositCents = 200,
					isEmployee = false
				)
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1200, transactionId = "tx-snack-plate")
		assertEquals(200, storedSales.first().depositCents)
		assertEquals(1000, storedSales.first().priceCents)
	}

	@Test
	fun multipleSnackTellers_multiplyPlateDepositCorrectly() {
		val drafts = listOf(
			SaleDraftEntry("Snack 1", 1200, "Snack 1", CategoryType.SNACKS.storageValue, "GROSS", 1000, 200, false),
			SaleDraftEntry("Snack 2", 1200, "Snack 2", CategoryType.SNACKS.storageValue, "GROSS", 1000, 200, false),
			SaleDraftEntry("Snack 3", 1200, "Snack 3", CategoryType.SNACKS.storageValue, "GROSS", 1000, 200, false)
		)

		checkoutService.checkout(drafts, finalTotalCents = 3600, transactionId = "tx-snack-multi")
		assertEquals(3, storedSales.size)
		assertEquals(600, storedSales.sumOf { it.depositCents })
	}

	@Test
	fun changedGlobalPlateDeposit_reflectedByDifferentDraftValues() {
		val firstSale = listOf(
			SaleDraftEntry("Snack alt", 1200, "Snack alt", CategoryType.SNACKS.storageValue, "GROSS", 1000, 200, false)
		)
		val secondSale = listOf(
			SaleDraftEntry("Snack neu", 1250, "Snack neu", CategoryType.SNACKS.storageValue, "GROSS", 1000, 250, false)
		)

		checkoutService.checkout(firstSale, finalTotalCents = 1200, transactionId = "tx-snack-old")
		checkoutService.checkout(secondSale, finalTotalCents = 1250, transactionId = "tx-snack-new")

		assertEquals(200, storedSales.first { it.transactionId == "tx-snack-old" }.depositCents)
		assertEquals(250, storedSales.first { it.transactionId == "tx-snack-new" }.depositCents)
	}

	@Test
	fun wine_withGlassDeposit_persistsDepositValue() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Wein mit Glaspfand",
				totalCents = 1250,
				articleName = "Wein",
				category = CategoryType.WEIN.storageValue,
				servingType = "STANDARD",
				priceCents = 1200,
				depositCents = 50,
				isEmployee = false
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1250, transactionId = "tx-wine-glass")
		assertEquals(50, storedSales.first().depositCents)
	}

	@Test
	fun wine_withBottleDeposit_persistsDepositValue() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Wein mit Flaschenpfand",
				totalCents = 1300,
				articleName = "Wein",
				category = CategoryType.WEIN.storageValue,
				servingType = "BOTTLE",
				priceCents = 1200,
				depositCents = 100,
				isEmployee = false
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1300, transactionId = "tx-wine-bottle")
		assertEquals(100, storedSales.first().depositCents)
	}

	@Test
	fun softdrink_withDeposit_persistsDepositValue() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Softdrink mit Pfand",
				totalCents = 380,
				articleName = "Cola",
				category = CategoryType.SOFTGETRAENKE.storageValue,
				servingType = "STANDARD",
				priceCents = 300,
				depositCents = 80,
				isEmployee = false
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 380, transactionId = "tx-soft-deposit")
		assertEquals(80, storedSales.first().depositCents)
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
