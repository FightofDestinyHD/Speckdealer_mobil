package com.speckdealer.app.data

import android.content.Context
import android.content.SharedPreferences

class GlobalSettingsStorage(
	context: Context,
	namespaceSuffix: String = "prod",
	private val articleStorage: ArticleStorage? = null
) {
	private val prefsName = if (namespaceSuffix == "dev") "speckdealer_global_settings_dev" else "speckdealer_global_settings"
	private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
	private val lock = Any()

	companion object {
		private const val KEY_GLASS_DEPOSIT_CENTS = "glass_deposit_cents"
		private const val KEY_BOTTLE_DEPOSIT_CENTS = "bottle_deposit_cents"
		private const val KEY_PLATE_DEPOSIT_CENTS = "plate_deposit_cents"
		private const val KEY_MIGRATED_FROM_ARTICLES = "migrated_from_articles"
	}

	fun loadDepositSettings(): GlobalDepositSettings {
		synchronized(lock) {
			ensureMigratedIfNeeded()
			return GlobalDepositSettings(
				glassDepositCents = prefs.getInt(KEY_GLASS_DEPOSIT_CENTS, 0).coerceAtLeast(0),
				bottleDepositCents = prefs.getInt(KEY_BOTTLE_DEPOSIT_CENTS, 0).coerceAtLeast(0),
				plateDepositCents = prefs.getInt(KEY_PLATE_DEPOSIT_CENTS, 0).coerceAtLeast(0)
			)
		}
	}

	fun saveDepositSettings(settings: GlobalDepositSettings) {
		synchronized(lock) {
			prefs.edit()
				.putInt(KEY_GLASS_DEPOSIT_CENTS, settings.glassDepositCents.coerceAtLeast(0))
				.putInt(KEY_BOTTLE_DEPOSIT_CENTS, settings.bottleDepositCents.coerceAtLeast(0))
				.putInt(KEY_PLATE_DEPOSIT_CENTS, settings.plateDepositCents.coerceAtLeast(0))
				.putBoolean(KEY_MIGRATED_FROM_ARTICLES, true)
				.apply()
		}
	}

	private fun ensureMigratedIfNeeded() {
		if (prefs.getBoolean(KEY_MIGRATED_FROM_ARTICLES, false)) return

		val migratedValues = migrateFromLegacyDepositArticles()
		prefs.edit()
			.putInt(KEY_GLASS_DEPOSIT_CENTS, migratedValues.glassDepositCents)
			.putInt(KEY_BOTTLE_DEPOSIT_CENTS, migratedValues.bottleDepositCents)
			.putInt(KEY_PLATE_DEPOSIT_CENTS, migratedValues.plateDepositCents)
			.putBoolean(KEY_MIGRATED_FROM_ARTICLES, true)
			.apply()
	}

	private fun migrateFromLegacyDepositArticles(): GlobalDepositSettings {
		val storage = articleStorage ?: return GlobalDepositSettings(0, 0, 0)

		val deposits = storage.getDepositArticles()
		val glass = deposits.firstOrNull { storage.resolveDepositType(it) == "glas" }?.priceCents ?: 0
		val bottle = deposits.firstOrNull { storage.resolveDepositType(it) == "flasche" }?.priceCents ?: 0
		val plate = deposits.firstOrNull { storage.resolveDepositType(it) == "teller" }?.priceCents ?: 0
		return GlobalDepositSettings(
			glassDepositCents = glass.coerceAtLeast(0),
			bottleDepositCents = bottle.coerceAtLeast(0),
			plateDepositCents = plate.coerceAtLeast(0)
		)
	}
}
