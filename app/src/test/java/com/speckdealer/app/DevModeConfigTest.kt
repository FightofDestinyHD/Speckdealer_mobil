package com.speckdealer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevModeConfigTest {

	@Test
	fun emptyPassword_isRejected() {
		DevModeConfig.deactivateSession()
		val result = DevModeConfig.checkPassword("")
		assertTrue(result is DevPasswordCheckResult.Empty)
		assertFalse(DevModeConfig.isSessionActive())
	}

	@Test
	fun wrongPassword_isRejected() {
		DevModeConfig.deactivateSession()
		val result = DevModeConfig.checkPassword("1111")
		assertTrue(result is DevPasswordCheckResult.Invalid)
		assertFalse(DevModeConfig.isSessionActive())
	}

	@Test
	fun correctPassword_unlocksDevMode() {
		DevModeConfig.deactivateSession()
		val result = DevModeConfig.checkPassword("8998")
		assertTrue(result is DevPasswordCheckResult.Success)
	}

	@Test
	fun appDataMode_requiresActiveSessionForDev() {
		DevModeConfig.deactivateSession()
		assertEquals(AppDataMode.MODE_PRODUCTION, AppDataMode.resolve(AppDataMode.MODE_DEV))
		DevModeConfig.activateSession()
		assertEquals(AppDataMode.MODE_DEV, AppDataMode.resolve(AppDataMode.MODE_DEV))
		DevModeConfig.deactivateSession()
	}
}
