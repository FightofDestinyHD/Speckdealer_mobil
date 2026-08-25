package com.speckdealer.app.data

data class DiscoveredSyncDevice(
	val deviceId: String,
	val displayName: String,
	val host: String,
	val port: Int,
	val lastSeenUtcMs: Long,
	val isPaired: Boolean = false
)

enum class PairingRequirement {
	AUTO_RECONNECT,
	REQUIRES_CONFIRMATION,
	INVALID
}

data class PairingDecision(
	val requirement: PairingRequirement,
	val reason: String
)

internal fun normalizeDeviceName(raw: String): String {
	return raw.trim().ifBlank { "Speckdealer-Gerät" }
}

internal fun normalizeDeviceId(raw: String): String {
	return raw.trim()
}

fun evaluatePairingRequirement(
	remoteDeviceId: String,
	remoteHost: String,
	remotePort: Int,
	pairedDeviceIds: Set<String>
): PairingDecision {
	val normalizedId = normalizeDeviceId(remoteDeviceId)
	if (normalizedId.isBlank()) {
		return PairingDecision(PairingRequirement.INVALID, "Geräte-ID fehlt")
	}
	if (remoteHost.isBlank() || remotePort !in 1024..65535) {
		return PairingDecision(PairingRequirement.INVALID, "Ungültige Host-/Portdaten")
	}
	if (pairedDeviceIds.contains(normalizedId)) {
		return PairingDecision(PairingRequirement.AUTO_RECONNECT, "Bereits gekoppeltes Gerät")
	}
	return PairingDecision(PairingRequirement.REQUIRES_CONFIRMATION, "Unbekanntes Gerät")
}

fun mergeVisibleDevices(
	existing: List<DiscoveredSyncDevice>,
	incoming: List<DiscoveredSyncDevice>,
	nowUtcMs: Long,
	staleAfterMs: Long = 45_000L
): List<DiscoveredSyncDevice> {
	val merged = linkedMapOf<String, DiscoveredSyncDevice>()
	fun keyFor(device: DiscoveredSyncDevice): String {
		val normalizedId = normalizeDeviceId(device.deviceId)
		return if (normalizedId.isNotBlank()) normalizedId else "${device.host}:${device.port}"
	}
	(existing + incoming).forEach { device ->
		if (device.host.isBlank() || device.port !in 1024..65535) return@forEach
		val key = keyFor(device)
		val previous = merged[key]
		if (previous == null || device.lastSeenUtcMs >= previous.lastSeenUtcMs) {
			merged[key] = device
		}
	}
	return merged.values
		.filter { nowUtcMs - it.lastSeenUtcMs <= staleAfterMs }
		.sortedBy { it.displayName.lowercase() }
}
