package com.speckdealer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPairingPolicyTest {

	@Test
	fun knownDevice_allowsAutoReconnect() {
		val result = evaluatePairingRequirement(
			remoteDeviceId = "device-1",
			remoteHost = "192.168.1.10",
			remotePort = 18550,
			pairedDeviceIds = setOf("device-1")
		)
		assertEquals(PairingRequirement.AUTO_RECONNECT, result.requirement)
	}

	@Test
	fun unknownDevice_requiresConfirmation() {
		val result = evaluatePairingRequirement(
			remoteDeviceId = "device-2",
			remoteHost = "192.168.1.11",
			remotePort = 18550,
			pairedDeviceIds = setOf("device-1")
		)
		assertEquals(PairingRequirement.REQUIRES_CONFIRMATION, result.requirement)
	}

	@Test
	fun invalidRemoteData_isRejected() {
		val result = evaluatePairingRequirement(
			remoteDeviceId = "",
			remoteHost = "",
			remotePort = 0,
			pairedDeviceIds = emptySet()
		)
		assertEquals(PairingRequirement.INVALID, result.requirement)
	}

	@Test
	fun mergeVisibleDevices_removesStaleAndDeduplicatesByDeviceId() {
		val now = 1_000_000L
		val existing = listOf(
			DiscoveredSyncDevice("a", "Alpha", "192.168.1.2", 18550, now - 5_000L),
			DiscoveredSyncDevice("stale", "Stale", "192.168.1.3", 18550, now - 100_000L)
		)
		val incoming = listOf(
			DiscoveredSyncDevice("a", "Alpha", "192.168.1.2", 18550, now - 1_000L),
			DiscoveredSyncDevice("b", "Beta", "192.168.1.4", 18550, now - 500L)
		)

		val merged = mergeVisibleDevices(existing, incoming, nowUtcMs = now)

		assertEquals(2, merged.size)
		assertTrue(merged.any { it.deviceId == "a" })
		assertTrue(merged.any { it.deviceId == "b" })
	}

	@Test
	fun mergeVisibleDevices_withoutIncomingSameNetwork_returnsOnlyFreshEntries() {
		val now = 2_000_000L
		val existing = listOf(
			DiscoveredSyncDevice("a", "Alpha", "192.168.10.2", 18550, now - 10_000L),
			DiscoveredSyncDevice("old", "Old", "10.0.0.7", 18550, now - 60_000L)
		)

		val merged = mergeVisibleDevices(existing, incoming = emptyList(), nowUtcMs = now)

		assertEquals(1, merged.size)
		assertEquals("a", merged.first().deviceId)
	}
}
