package com.speckdealer.app

import com.speckdealer.app.data.OrderRecord
import com.speckdealer.app.data.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdersVisibilityLogicTest {

	@Test
	fun visibleOpenOrders_keepsOnlyNonFinalStatuses_andSortsByCreatedAt() {
		val completed = sampleOrder("o3", 3000L, OrderStatus.COMPLETED.name)
		val openOlder = sampleOrder("o1", 1000L, OrderStatus.OPEN.name)
		val cancelled = sampleOrder("o4", 4000L, OrderStatus.CANCELLED.name)
		val openNewer = sampleOrder("o2", 2000L, OrderStatus.PENDING_SYNC.name)

		val visible = visibleOpenOrders(listOf(completed, openNewer, cancelled, openOlder))

		assertEquals(2, visible.size)
		assertEquals(listOf("o1", "o2"), visible.map { it.id })
		assertTrue(visible.all { it.status != OrderStatus.COMPLETED.name && it.status != OrderStatus.CANCELLED.name })
	}

	@Test
	fun buildDetailsText_containsOnlyRelevantOrderDetails_withoutInternalStatusOrTimeOrQuantity() {
		val order = sampleOrder("o5", 5000L, OrderStatus.PENDING_SYNC.name)
		val details = order.buildDetailsText()

		assertTrue(details.contains("Sonderwunsch:"))
		assertFalse(details.contains("PENDING_SYNC"))
		assertFalse(details.contains("Status:"))
		assertFalse(details.contains("Uhrzeit:"))
		assertFalse(details.contains("Anzahl:"))
	}

	private fun sampleOrder(id: String, createdAt: Long, status: String): OrderRecord {
		return OrderRecord(
			id = id,
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
			sonderwunsch = "ohne Tomaten",
			glaesser01 = 0,
			glaesser02 = 0,
			transactionId = "tx-$id",
			timestampMs = createdAt,
			createdAtUtcMs = createdAt,
			updatedAtUtcMs = createdAt,
			sourceDeviceId = "device-a",
			status = status,
			syncVersion = 1L,
			isTestData = false
		)
	}
}
