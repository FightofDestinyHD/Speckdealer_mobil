package com.speckdealer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderStorageMergePolicyTest {

	@Test
	fun equalVersionAndTimestamps_prefersRecordWithSourceDeviceId() {
		val merge = InMemoryMerge()
		val base = sampleOrder(sourceDeviceId = "", status = OrderStatus.PENDING_SYNC.name)
		val incoming = sampleOrder(sourceDeviceId = "device-b", status = OrderStatus.PENDING_SYNC.name)

		merge.addAll(listOf(base, incoming))

		val merged = merge.loadAll().single()
		assertEquals("device-b", merged.sourceDeviceId)
	}

	@Test
	fun equalVersionAndTimestamps_prefersAdvancedStatusOverPendingSync() {
		val merge = InMemoryMerge()
		val base = sampleOrder(sourceDeviceId = "device-a", status = OrderStatus.PENDING_SYNC.name)
		val incoming = sampleOrder(sourceDeviceId = "device-a", status = OrderStatus.OPEN.name)

		merge.addAll(listOf(base, incoming))

		val merged = merge.loadAll().single()
		assertEquals(OrderStatus.OPEN.name, merged.status)
	}

	@Test
	fun sameStableId_isNotDuplicatedAfterRepeatedIncomingSync() {
		val merge = InMemoryMerge()
		val order = sampleOrder(sourceDeviceId = "device-a", status = OrderStatus.OPEN.name)

		merge.addAll(listOf(order, order, order.copy(updatedAtUtcMs = order.updatedAtUtcMs)))

		assertEquals(1, merge.loadAll().size)
	}

	private fun sampleOrder(sourceDeviceId: String, status: String): OrderRecord {
		return OrderRecord(
			id = "stable-order-id-1",
			articleName = "Snackteller",
			sizeName = "Groß",
			priceCents = 1200,
			depositCents = 200,
			isEmployee = false,
			gurken = 1,
			tomaten = 1,
			zwiebeln = 1,
			oliven = 1,
			brezeln = 1,
			sonderwunsch = "mehr Oliven",
			glaesser01 = 0,
			glaesser02 = 0,
			transactionId = "tx-1",
			timestampMs = 1000L,
			createdAtUtcMs = 1000L,
			updatedAtUtcMs = 1000L,
			sourceDeviceId = sourceDeviceId,
			status = status,
			syncVersion = 1L,
			isTestData = false
		)
	}

	private class InMemoryMerge {
		private val list = mutableListOf<OrderRecord>()

		fun addAll(orders: List<OrderRecord>) {
			for (incoming in orders) {
				val idx = list.indexOfFirst { it.id == incoming.id }
				if (idx < 0) {
					list.add(incoming)
				} else {
					val existing = list[idx]
					if (shouldReplace(existing, incoming)) {
						list[idx] = incoming
					}
				}
			}
		}

		private fun shouldReplace(existing: OrderRecord, incoming: OrderRecord): Boolean {
			if (incoming.syncVersion > existing.syncVersion) return true
			if (incoming.syncVersion < existing.syncVersion) return false
			if (incoming.updatedAtUtcMs > existing.updatedAtUtcMs) return true
			if (incoming.updatedAtUtcMs < existing.updatedAtUtcMs) return false
			if (incoming.timestampMs > existing.timestampMs) return true
			if (incoming.timestampMs < existing.timestampMs) return false
			if (existing.sourceDeviceId.isBlank() && incoming.sourceDeviceId.isNotBlank()) return true
			if (existing.status == OrderStatus.PENDING_SYNC.name && incoming.status != existing.status) return true
			return false
		}

		fun loadAll(): List<OrderRecord> {
			assertTrue(list.isNotEmpty())
			return list.toList()
		}
	}
}
