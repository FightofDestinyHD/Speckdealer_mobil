package com.speckdealer.app.data

import android.content.Context

object LocalOrderSyncRegistry {
	private val managers = mutableMapOf<String, LocalOrderSyncManager>()
	private val lock = Any()

	fun get(context: Context, dataMode: String): LocalOrderSyncManager {
		return synchronized(lock) {
			managers.getOrPut(dataMode) {
				LocalOrderSyncManager(context.applicationContext, dataMode)
			}
		}
	}
}
