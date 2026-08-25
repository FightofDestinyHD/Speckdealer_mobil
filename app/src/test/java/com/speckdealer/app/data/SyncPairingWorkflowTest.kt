package com.speckdealer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPairingWorkflowTest {

	@Test
	fun unknownDevice_cannotProceedWithoutConfirmation() {
		val decision = evaluatePairingRequirement(
			remoteDeviceId = "new-device",
			remoteHost = "192.168.0.20",
			remotePort = 18550,
			pairedDeviceIds = setOf("known-device")
		)
		assertEquals(PairingRequirement.REQUIRES_CONFIRMATION, decision.requirement)
	}

	@Test
	fun knownDevice_canReconnectAutomatically() {
		val decision = evaluatePairingRequirement(
			remoteDeviceId = "known-device",
			remoteHost = "192.168.0.21",
			remotePort = 18550,
			pairedDeviceIds = setOf("known-device")
		)
		assertEquals(PairingRequirement.AUTO_RECONNECT, decision.requirement)
	}

	@Test
	fun invalidNetworkEndpoint_isRejected() {
		val decision = evaluatePairingRequirement(
			remoteDeviceId = "x",
			remoteHost = "",
			remotePort = 10,
			pairedDeviceIds = emptySet()
		)
		assertEquals(PairingRequirement.INVALID, decision.requirement)
	}

	@Test
	fun normalizeDeviceName_fallbackWorks() {
		assertEquals("Speckdealer-Gerät", normalizeDeviceName("   "))
		assertTrue(normalizeDeviceName("Kasse 1").startsWith("Kasse"))
	}
}
