package com.speckdealer.app.data

import android.content.Context
import com.speckdealer.app.AppDataMode
import java.util.UUID

object DeviceIdentityStore {
	private const val PREFS = "speckdealer_device_identity"
	private const val KEY_PROD = "device_id_prod"
	private const val KEY_DEV = "device_id_dev"

	fun getOrCreate(context: Context, dataMode: String): String {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		val key = if (AppDataMode.isDev(dataMode)) KEY_DEV else KEY_PROD
		val existing = prefs.getString(key, null)?.takeIf { it.isNotBlank() }
		if (existing != null) return existing
		val created = UUID.randomUUID().toString()
		prefs.edit().putString(key, created).apply()
		return created
	}
}
