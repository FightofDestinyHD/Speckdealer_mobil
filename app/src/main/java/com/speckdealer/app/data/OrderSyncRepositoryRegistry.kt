package com.speckdealer.app.data

import android.content.Context

object OrderSyncRepositoryRegistry {
	private val repositories = mutableMapOf<String, OrderSyncRepository>()
	private val lock = Any()

	fun get(context: Context, dataMode: String): OrderSyncRepository {
		return synchronized(lock) {
			repositories.getOrPut(dataMode) {
				OrderSyncRepository(context.applicationContext, dataMode)
			}
		}
	}
}
