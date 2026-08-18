package com.speckdealer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDataModeTest {

	@Test
	fun unknownMode_fallsBackToProduction() {
		DevModeConfig.deactivateSession()
		assertEquals(AppDataMode.MODE_PRODUCTION, AppDataMode.resolve("UNKNOWN"))
	}

	@Test
	fun devMode_withoutSession_fallsBackToProduction() {
		DevModeConfig.deactivateSession()
		assertEquals(AppDataMode.MODE_PRODUCTION, AppDataMode.resolve(AppDataMode.MODE_DEV))
	}
}
