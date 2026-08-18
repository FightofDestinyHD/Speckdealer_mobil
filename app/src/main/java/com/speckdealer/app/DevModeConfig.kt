package com.speckdealer.app

import java.util.concurrent.atomic.AtomicBoolean

object DevModeConfig {

	const val DEV_PASSWORD_DISCLAIMER = "Ein in der APK hinterlegtes Passwort schützt nur vor versehentlicher Nutzung und ist kein Hochsicherheitsmechanismus."
	private const val DEV_PASSWORD_HASH = "d10a5bdca9f228ccad60d148fc253df9f4687abeb45e078818ade77d995898af"
	private const val MAX_FAILED_ATTEMPTS = 3
	private const val LOCK_MS = 20_000L

	private val devSessionActive = AtomicBoolean(false)
	private var failedAttempts: Int = 0
	private var lockUntilRealtimeMs: Long = 0L

	@Volatile
	var isDevEntryEnabled: Boolean = true
		private set

	fun setDevEntryEnabled(enabled: Boolean) {
		isDevEntryEnabled = enabled
		if (!enabled) {
			deactivateSession()
		}
	}

	fun isSessionActive(): Boolean = devSessionActive.get()

	fun activateSession() {
		devSessionActive.set(true)
	}

	fun deactivateSession() {
		devSessionActive.set(false)
	}

	fun checkPassword(input: String): DevPasswordCheckResult {
		val now = System.currentTimeMillis()
		if (now < lockUntilRealtimeMs) {
			val remaining = (lockUntilRealtimeMs - now).coerceAtLeast(1000L)
			return DevPasswordCheckResult.Locked(remainingMs = remaining)
		}

		if (input.isBlank()) {
			return DevPasswordCheckResult.Empty
		}

		val normalized = input.trim()
		val hash = CryptoSupport.sha256(normalized)
		if (hash == DEV_PASSWORD_HASH) {
			failedAttempts = 0
			lockUntilRealtimeMs = 0L
			return DevPasswordCheckResult.Success
		}

		failedAttempts += 1
		if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
			failedAttempts = 0
			lockUntilRealtimeMs = now + LOCK_MS
			return DevPasswordCheckResult.Locked(remainingMs = LOCK_MS)
		}
		return DevPasswordCheckResult.Invalid
	}
}

sealed class DevPasswordCheckResult {
	data object Success : DevPasswordCheckResult()
	data object Empty : DevPasswordCheckResult()
	data object Invalid : DevPasswordCheckResult()
	data class Locked(val remainingMs: Long) : DevPasswordCheckResult()
}

object AppDataMode {
	const val EXTRA_DATA_MODE = "extra_data_mode"
	const val MODE_PRODUCTION = "PRODUCTION"
	const val MODE_DEV = "DEV"

	fun resolve(modeExtra: String?): String {
		return if (modeExtra == MODE_DEV && DevModeConfig.isSessionActive()) MODE_DEV else MODE_PRODUCTION
	}

	fun isDev(mode: String): Boolean = mode == MODE_DEV
}

private object CryptoSupport {
	fun sha256(value: String): String {
		val digest = java.security.MessageDigest.getInstance("SHA-256")
		val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
		return bytes.joinToString("") { "%02x".format(it) }
	}
}
