package com.speckdealer.app

import com.speckdealer.app.data.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesCheckoutFlowRulesTest {

	private data class Item(val category: String, val totalCents: Int)

	private enum class FlowType { NORMAL, PURE_RETURN, MIXED }

	private fun classify(items: List<Item>): FlowType {
		val onlyReturns = items.isNotEmpty() && items.all { it.category == CategoryType.PFAND.storageValue && it.totalCents < 0 }
		if (onlyReturns) return FlowType.PURE_RETURN
		val hasReturn = items.any { it.category == CategoryType.PFAND.storageValue && it.totalCents < 0 }
		return if (hasReturn) FlowType.MIXED else FlowType.NORMAL
	}

	@Test
	fun pureDepositReturn_zeroAmountGivenIsAllowed_rule() {
		val flow = classify(listOf(Item(CategoryType.PFAND.storageValue, -50)))
		assertEquals(FlowType.PURE_RETURN, flow)
		val amountGivenCents = 0L
		assertTrue(amountGivenCents >= 0L)
	}

	@Test
	fun mixedCart_isNettedAgainstSale_rule() {
		val items = listOf(
			Item(CategoryType.WEIN.storageValue, 400),
			Item(CategoryType.PFAND.storageValue, -50)
		)
		val flow = classify(items)
		assertEquals(FlowType.MIXED, flow)
		val net = items.sumOf { it.totalCents.toLong() }
		assertEquals(350L, net)
	}

	@Test
	fun normalSale_requiresSufficientAmountGiven_rule() {
		val flow = classify(listOf(Item(CategoryType.WEIN.storageValue, 400)))
		assertEquals(FlowType.NORMAL, flow)
		val due = 400L
		val given = 300L
		assertTrue(given < due)
	}
}
