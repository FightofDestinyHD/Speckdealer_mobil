package com.speckdealer.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailySalesStorageRecoveryInstrumentedTest {

	private lateinit var context: Context

	@Before
	fun setup() {
		context = ApplicationProvider.getApplicationContext()
		context.getSharedPreferences("speckdealer_daily_sales", Context.MODE_PRIVATE).edit().clear().commit()
	}

	@Test
	fun corruptedLegacyJson_recoversFromBackupAndKeepsCorruptedPayload() {
		val prefs = context.getSharedPreferences("speckdealer_daily_sales", Context.MODE_PRIVATE)
		val backupArray = JSONArray().put(
			SaleRecord(
				articleName = "Backup",
				category = CategoryType.SOFTGETRAENKE.storageValue,
				servingType = "STANDARD",
				priceCents = 100,
				depositCents = 0,
				isEmployee = false,
				checkoutId = "c1",
				transactionId = "t1",
				recordId = "r1",
				timestampMs = 1L
			).toJson()
		).toString()
		prefs.edit()
			.putString("sale_records", "{kaputt")
			.putString("sale_records_backup", backupArray)
			.commit()

		val storage = DailySalesStorage(context)
		val loaded = storage.loadAll()

		assertEquals(1, loaded.size)
		assertEquals("Backup", loaded.first().articleName)
		assertNotNull(prefs.getString("sale_records_corrupted_payload", null))
	}
}
