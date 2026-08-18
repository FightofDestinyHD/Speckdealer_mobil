package com.speckdealer.app.data

import android.content.Context
import com.speckdealer.app.AppDataMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OrderSyncRepository(
	private val context: Context,
	private val dataMode: String
) {
	private val storage: OrderStorage = DataModeAwareStorageFactory.orderStorage(context, dataMode)
	private val deviceId: String = DeviceIdentityStore.getOrCreate(context, dataMode)
	private val isTestData: Boolean = AppDataMode.isDev(dataMode)
	private val ordersFlow = MutableStateFlow(storage.loadAll().toList())

	fun localDeviceId(): String = deviceId

	fun orders(): StateFlow<List<OrderRecord>> = ordersFlow

	fun loadAll(): List<OrderRecord> = storage.loadAll()

	fun upsertIncoming(orders: List<OrderRecord>) {
		if (orders.isEmpty()) return
		val sanitized = orders
			.filter { it.id.isNotBlank() }
			.map { incoming ->
				incoming.copy(
					syncVersion = incoming.syncVersion.coerceAtLeast(1L),
					createdAtUtcMs = incoming.createdAtUtcMs.coerceAtLeast(1L),
					updatedAtUtcMs = incoming.updatedAtUtcMs.coerceAtLeast(incoming.createdAtUtcMs),
					isTestData = incoming.isTestData || isTestData
				)
			}
		if (sanitized.isNotEmpty()) {
			storage.addAll(sanitized)
			emitCurrentOrders()
		}
	}

	fun markOrderStatus(orderId: String, status: OrderStatus): OrderRecord? {
		if (orderId.isBlank()) return null
		val order = storage.loadAll().firstOrNull { it.id == orderId } ?: return null
		val updated = order.withStatus(status).copy(sourceDeviceId = deviceId)
		storage.addAll(listOf(updated))
		emitCurrentOrders()
		return updated
	}

	fun attachMetadataForLocal(orders: List<OrderRecord>): List<OrderRecord> {
		val now = System.currentTimeMillis()
		return orders.map { order ->
			val createdAt = order.createdAtUtcMs.takeIf { it > 0 } ?: now
			val updatedAt = order.updatedAtUtcMs.takeIf { it > 0 } ?: createdAt
			val version = order.syncVersion.coerceAtLeast(1L)
			order.copy(
				createdAtUtcMs = createdAt,
				updatedAtUtcMs = updatedAt,
				sourceDeviceId = if (order.sourceDeviceId.isBlank()) deviceId else order.sourceDeviceId,
				status = if (order.status.isBlank()) OrderStatus.OPEN.name else order.status,
				syncVersion = version,
				isTestData = order.isTestData || isTestData
			)
		}
	}

	fun notifyLocalChange() {
		emitCurrentOrders()
	}

	private fun emitCurrentOrders() {
		ordersFlow.value = storage.loadAll().toList()
	}
}
