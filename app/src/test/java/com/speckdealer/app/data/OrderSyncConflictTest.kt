package com.speckdealer.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderSyncConflictTest {

	@Test
	fun newerVersion_replacesOlderVersion_forSameOrderId() {
		val storage = InMemoryOrderMerge()
		val oldOrder = sampleOrder(syncVersion = 1, updatedAt = 1000L, status = OrderStatus.OPEN.name)
		val newOrder = sampleOrder(syncVersion = 2, updatedAt = 2000L, status = OrderStatus.READY.name)
		storage.addAll(listOf(oldOrder))
		storage.addAll(listOf(newOrder))
		val merged = storage.loadAll().first()
		assertEquals(2L, merged.syncVersion)
		assertEquals(OrderStatus.READY.name, merged.status)
	}

	@Test
	fun sameVersion_olderTimestamp_isIgnored() {
		val storage = InMemoryOrderMerge()
		val base = sampleOrder(syncVersion = 2, updatedAt = 2000L, status = OrderStatus.READY.name)
		val older = sampleOrder(syncVersion = 2, updatedAt = 1500L, status = OrderStatus.OPEN.name)
		storage.addAll(listOf(base))
		storage.addAll(listOf(older))
		val merged = storage.loadAll().first()
		assertEquals(OrderStatus.READY.name, merged.status)
	}

	@Test
	fun repeatedSync_doesNotDuplicateOrderId() {
		val storage = InMemoryOrderMerge()
		val order = sampleOrder(syncVersion = 1, updatedAt = 1000L)
		storage.addAll(listOf(order, order, order.copy(syncVersion = 1, updatedAtUtcMs = 1000L)))
		assertEquals(1, storage.loadAll().size)
	}

	private fun sampleOrder(syncVersion: Long, updatedAt: Long, status: String = OrderStatus.OPEN.name): OrderRecord {
		return OrderRecord(
			id = "order-1",
			articleName = "Snackteller",
			sizeName = "Groß",
			priceCents = 1200,
			depositCents = 0,
			isEmployee = false,
			gurken = 1,
			tomaten = 1,
			zwiebeln = 1,
			oliven = 1,
			brezeln = 1,
			sonderwunsch = "",
			glaesser01 = 0,
			glaesser02 = 0,
			transactionId = "tx-1",
			timestampMs = 1000L,
			createdAtUtcMs = 1000L,
			updatedAtUtcMs = updatedAt,
			sourceDeviceId = "device-A",
			status = status,
			syncVersion = syncVersion,
			isTestData = false
		)
	}

	private class InMemoryOrderMerge {
		private val list = mutableListOf<OrderRecord>()

		fun addAll(orders: List<OrderRecord>) {
			for (incoming in orders) {
				val idx = list.indexOfFirst { it.id == incoming.id }
				if (idx < 0) {
					list.add(incoming)
				} else {
					val existing = list[idx]
					val replace = incoming.syncVersion > existing.syncVersion ||
						(incoming.syncVersion == existing.syncVersion && incoming.updatedAtUtcMs > existing.updatedAtUtcMs) ||
						(incoming.syncVersion == existing.syncVersion && incoming.updatedAtUtcMs == existing.updatedAtUtcMs && incoming.timestampMs > existing.timestampMs)
					if (replace) list[idx] = incoming
				}
			}
		}

		fun loadAll(): List<OrderRecord> = list.toList()
	}
}
