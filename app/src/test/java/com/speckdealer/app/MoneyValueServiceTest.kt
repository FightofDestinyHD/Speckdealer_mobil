package com.speckdealer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyValueServiceTest {

	@Test
	fun parse_acceptsCommaAndDot() {
		val comma = MoneyValueService.parseAmountToCents("12,50")
		val dot = MoneyValueService.parseAmountToCents("12.50")
		assertTrue(comma.isValid)
		assertTrue(dot.isValid)
		assertEquals(1250L, comma.cents)
		assertEquals(1250L, dot.cents)
	}

	@Test
	fun parse_rejectsEmptyInvalidNegativeZeroAndTooLarge() {
		assertEquals(MoneyValueService.ParseError.EMPTY, MoneyValueService.parseAmountToCents("").error)
		assertEquals(MoneyValueService.ParseError.INVALID_FORMAT, MoneyValueService.parseAmountToCents("abc").error)
		assertEquals(MoneyValueService.ParseError.INVALID_FORMAT, MoneyValueService.parseAmountToCents("12.345").error)
		assertEquals(MoneyValueService.ParseError.INVALID_FORMAT, MoneyValueService.parseAmountToCents("-1.00").error)
		assertEquals(MoneyValueService.ParseError.ZERO_OR_NEGATIVE, MoneyValueService.parseAmountToCents("0").error)
		assertEquals(MoneyValueService.ParseError.TOO_LARGE, MoneyValueService.parseAmountToCents("10000000.00").error)
	}

	@Test
	fun parse_allowsZeroWhenConfigured() {
		val result = MoneyValueService.parseAmountToCents("0", allowZero = true)
		assertTrue(result.isValid)
		assertEquals(0L, result.cents)
	}

	@Test
	fun allocation_isExactAndStable() {
		val shares = MoneyValueService.allocateProportionally(
			weights = listOf(333L, 333L, 334L),
			totalCents = 1000L
		)
		assertEquals(3, shares.size)
		assertEquals(1000L, shares.sum())
		assertTrue(shares.all { it >= 0L })
	}

	@Test
	fun allocation_handlesZeroWeightsWithoutCrash() {
		val shares = MoneyValueService.allocateProportionally(
			weights = listOf(0L, 0L, 0L),
			totalCents = 5L
		)
		assertEquals(3, shares.size)
		assertEquals(5L, shares.sum())
		assertFalse(shares.any { it < 0L })
	}
}
