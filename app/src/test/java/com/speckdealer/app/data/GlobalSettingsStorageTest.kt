package com.speckdealer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GlobalSettingsStorageTest {

	@Test
	fun categoryDefaultOrder_doesNotExposePfandCategory() {
		val order = CategoryType.defaultOrder()
		assertEquals(6, order.size)
		assertEquals(false, order.contains(CategoryType.PFAND))
	}

	@Test
	fun globalDepositSettings_amountForToken_mapsGlassBottlePlate() {
		val settings = GlobalDepositSettings(
			glassDepositCents = 50,
			bottleDepositCents = 25,
			plateDepositCents = 200
		)

		assertEquals(50, settings.amountForToken("glas"))
		assertEquals(50, settings.amountForToken("glass_01"))
		assertEquals(25, settings.amountForToken("flasche"))
		assertEquals(25, settings.amountForToken("bottle"))
		assertEquals(200, settings.amountForToken("teller"))
		assertEquals(0, settings.amountForToken("unknown"))
	}

	@Test
	fun globalDepositSettings_canRepresentZeroValues() {
		val settings = GlobalDepositSettings(0, 0, 0)
		assertEquals(0, settings.amountForToken("glas"))
		assertEquals(0, settings.amountForToken("flasche"))
		assertEquals(0, settings.amountForToken("teller"))
		assertNotNull(settings)
	}
}
