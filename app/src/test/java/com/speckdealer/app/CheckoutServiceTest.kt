package com.speckdealer.app

import com.speckdealer.app.data.CategoryType
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
	private lateinit var checkoutService: CheckoutService

	@Before
	fun setup() {
		storedSales.clear()
		storedOrders.clear()
		checkoutService = CheckoutService(
			appendSales = { storedSales.addAll(it) },
			appendOrders = { storedOrders.addAll(it) }
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

		checkoutService.checkout(drafts, finalTotalCents = 1000)

		assertEquals(1, storedSales.size)
		assertEquals(1, storedOrders.size)
	}

	@Test
	fun angebot_isSavedExactlyOnce_withBottleHelperRecord() {
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
				orderDraft = OrderDraftPayload(
					articleName = "Angebotsteller",
					sizeName = "Groß",
					basePriceCents = 1500,
					depositCents = 300,
					isEmployee = false,
					glaesser01 = 1,
					glaesser02 = 1
				)
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1500)

		assertEquals(2, storedSales.size)
		assertEquals(1, storedSales.count { it.servingType == "BOTTLE" })
		assertEquals(1, storedOrders.size)
	}

	@Test
	fun wineBottleAndWineGlass_areSavedWithCorrectServingTypes() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Wein Flasche",
				totalCents = 1000,
				articleName = "Wein A",
				category = CategoryType.WEIN.storageValue,
				servingType = "BOTTLE",
				priceCents = 1000,
				depositCents = 0,
				isEmployee = false
			),
			SaleDraftEntry(
				displayName = "Wein Glas",
				totalCents = 500,
				articleName = "Wein A",
				category = CategoryType.WEIN.storageValue,
				servingType = "GLASS_01",
				priceCents = 500,
				depositCents = 100,
				isEmployee = false
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 1500)

		assertEquals(2, storedSales.size)
		assertEquals(1, storedSales.count { it.servingType == "BOTTLE" })
		assertEquals(1, storedSales.count { it.servingType == "GLASS_01" })
	}

	@Test
	fun employeeSale_isStoredAsEmployeeAndZeroPriceAfterAllocation() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "MA Getränk",
				totalCents = 1200,
				articleName = "MA Getränk",
				category = CategoryType.SOFTGETRAENKE.storageValue,
				priceCents = 1200,
				depositCents = 0,
				isEmployee = true
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 0)

		val sale = storedSales.single()
		assertTrue(sale.isEmployee)
		assertEquals(0, sale.priceCents)
	}

	@Test
	fun deposit_isPersistedCorrectly() {
		val drafts = listOf(
			SaleDraftEntry(
				displayName = "Soft mit Pfand",
				totalCents = 900,
				articleName = "Cola",
				category = CategoryType.SOFTGETRAENKE.storageValue,
				priceCents = 900,
				depositCents = 200,
				isEmployee = false
			)
		)

		checkoutService.checkout(drafts, finalTotalCents = 900)

		val sale = storedSales.single()
		assertEquals(200, sale.depositCents)
	}

	@Test
	fun discountAndRounding_allocatesExactlyFinalTotal() {
		val drafts = listOf(
			SaleDraftEntry("A", 500, "A", CategoryType.SOFTGETRAENKE.storageValue, priceCents = 500, depositCents = 0, isEmployee = false),
			SaleDraftEntry("B", 500, "B", CategoryType.SOFTGETRAENKE.storageValue, priceCents = 500, depositCents = 0, isEmployee = false),
			SaleDraftEntry("C", 500, "C", CategoryType.SOFTGETRAENKE.storageValue, priceCents = 500, depositCents = 0, isEmployee = false)
		)

		checkoutService.checkout(drafts, finalTotalCents = 999)

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
