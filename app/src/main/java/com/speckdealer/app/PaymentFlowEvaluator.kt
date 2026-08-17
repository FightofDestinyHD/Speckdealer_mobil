package com.speckdealer.app

import java.util.concurrent.atomic.AtomicBoolean

sealed class PaymentDecision {
	object CartEmpty : PaymentDecision()
	object Cancelled : PaymentDecision()
	data class Underpaid(val missingCents: Long) : PaymentDecision()
	data class Accepted(val changeCents: Long) : PaymentDecision()
}

object PaymentFlowEvaluator {
	fun evaluate(
		cartTotalCents: Long,
		givenCents: Long?,
		cancelled: Boolean
	): PaymentDecision {
		if (cartTotalCents <= 0L) return PaymentDecision.CartEmpty
		if (cancelled) return PaymentDecision.Cancelled
		val paid = givenCents ?: return PaymentDecision.Cancelled
		return if (paid < cartTotalCents) {
			PaymentDecision.Underpaid(cartTotalCents - paid)
		} else {
			PaymentDecision.Accepted(paid - cartTotalCents)
		}
	}
}

class CheckoutTriggerGuard {
	private val inProgress = AtomicBoolean(false)

	fun tryStart(): Boolean = inProgress.compareAndSet(false, true)

	fun finish() {
		inProgress.set(false)
	}
}
