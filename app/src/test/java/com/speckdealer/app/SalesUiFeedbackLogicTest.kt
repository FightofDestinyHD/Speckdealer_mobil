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
	fun blockingStateDialog_isShownOnlyForError() {
		assertFalse(shouldShowBlockingStateDialog(UiOperationState.Success("Kassiervorgang gespeichert")))
		assertTrue(shouldShowBlockingStateDialog(UiOperationState.Error("Speichern fehlgeschlagen")))
	}

	@Test
	fun changeDialogMessage_formatsChangeAndPayoutAndZero() {
		val change = buildChangeDialogMessage(250L, formatter)
		val payout = buildChangeDialogMessage(-200L, formatter)
		val zero = buildChangeDialogMessage(0L, formatter)

		assertTrue(change.contains("Rückgeld:"))
		assertTrue(payout.contains("Auszahlung:"))
		assertEquals("Kein Rückgeld / keine zusätzliche Auszahlung", zero)
	}
}
