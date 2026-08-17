package com.speckdealer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentFlowEvaluatorTest {

	@Test
	fun emptyCart_returnsCartEmpty() {
		val decision = PaymentFlowEvaluator.evaluate(
			cartTotalCents = 0L,
			givenCents = 100L,
			cancelled = false
		)
		assertTrue(decision is PaymentDecision.CartEmpty)
	}

	@Test
	fun underpayment_returnsMissingAmount() {
		val decision = PaymentFlowEvaluator.evaluate(
			cartTotalCents = 1500L,
			givenCents = 1000L,
			cancelled = false
		)
		assertTrue(decision is PaymentDecision.Underpaid)
		assertEquals(500L, (decision as PaymentDecision.Underpaid).missingCents)
	}

	@Test
	fun exactPayment_returnsAcceptedWithZeroChange() {
		val decision = PaymentFlowEvaluator.evaluate(
			cartTotalCents = 1500L,
			givenCents = 1500L,
			cancelled = false
		)
		assertTrue(decision is PaymentDecision.Accepted)
		assertEquals(0L, (decision as PaymentDecision.Accepted).changeCents)
	}

	@Test
	fun overpayment_returnsAcceptedWithChange() {
		val decision = PaymentFlowEvaluator.evaluate(
			cartTotalCents = 1500L,
			givenCents = 2000L,
			cancelled = false
		)
		assertTrue(decision is PaymentDecision.Accepted)
		assertEquals(500L, (decision as PaymentDecision.Accepted).changeCents)
	}

	@Test
	fun invalidNegativeOrTooLargeAmount_returnsInvalidAmount() {
		val negative = PaymentFlowEvaluator.evaluate(
			cartTotalCents = 100L,
			givenCents = -1L,
			cancelled = false
		)
		val tooLarge = PaymentFlowEvaluator.evaluate(
			cartTotalCents = 100L,
			givenCents = MoneyValueService.MAX_ALLOWED_CENTS + 1L,
			cancelled = false
		)
		assertTrue(negative is PaymentDecision.InvalidAmount)
		assertTrue(tooLarge is PaymentDecision.InvalidAmount)
	}

	@Test
	fun cancelledPayment_returnsCancelled() {
		val decision = PaymentFlowEvaluator.evaluate(
			cartTotalCents = 1500L,
			givenCents = null,
			cancelled = true
		)
		assertTrue(decision is PaymentDecision.Cancelled)
	}

	@Test
	fun checkoutTriggerGuard_blocksSecondStartUntilFinish() {
		val guard = CheckoutTriggerGuard()
		assertTrue(guard.tryStart())
		assertFalse(guard.tryStart())
		guard.finish()
		assertTrue(guard.tryStart())
	}
}
