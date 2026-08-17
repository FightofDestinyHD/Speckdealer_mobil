package com.speckdealer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepositReturnDialogConfigTest {

	@Test
	fun depositReturnType_hasAlwaysThreeVisibleOptions() {
		val values = MainActivity.DepositReturnType.entries
		assertEquals(3, values.size)
		assertEquals("Flasche", values[0].displayName)
		assertEquals("Glas", values[1].displayName)
		assertEquals("Teller", values[2].displayName)
	}

	@Test
	fun glassLabel_hasNoTypo() {
		val labels = MainActivity.DepositReturnType.entries.map { it.displayName }
		assertTrue(labels.contains("Glas"))
		assertFalse(labels.contains("Gals"))
	}

	@Test
	fun glassLookup_supportsStableTokens() {
		val glassTokens = MainActivity.DepositReturnType.GLASS.amountLookupTokens
		assertTrue(glassTokens.contains("glas"))
		assertTrue(glassTokens.contains("glass"))
		assertTrue(glassTokens.contains("glass_01"))
		assertTrue(glassTokens.contains("glass_02"))
	}
}
