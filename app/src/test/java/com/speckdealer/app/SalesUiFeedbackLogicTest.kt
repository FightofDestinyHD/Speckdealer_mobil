package com.speckdealer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale

class SalesUiFeedbackLogicTest {

	private val formatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)

	@Test
	fun checkoutSuccessFeedback_isConfiguredFor500ms() {
		assertEquals(500L, CHECKOUT_SUCCESS_FEEDBACK_MS)
	}

	@Test
	fun blockingStateDialog_isShownOnlyForError() {
		assertFalse(shouldShowBlockingStateDialog(UiOperationState.Success("Kassiervorgang gespeichert")))
		assertTrue(shouldShowBlockingStateDialog(UiOperationState.Error("Speichern fehlgeschlagen")))
	}

	@Test
	fun checkoutSuccessMessage_formatsChangeAndPayoutAndZero() {
		val change = buildCheckoutSuccessMessage(250L, formatter)
		val payout = buildCheckoutSuccessMessage(-200L, formatter)
		val zero = buildCheckoutSuccessMessage(0L, formatter)

		assertTrue(change.contains("Rückgeld:"))
		assertTrue(payout.contains("Auszahlung:"))
		assertEquals("Kassiert ✓", zero)
	}
}
