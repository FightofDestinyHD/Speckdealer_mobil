package com.speckdealer.app.data

import android.content.Context
import com.speckdealer.app.AppDataMode

object DataModeAwareStorageFactory {

	fun dailySalesStorage(context: Context, dataMode: String): DailySalesStorage {
		return DailySalesStorage(context, namespaceSuffix(dataMode))
	}

	fun depositMovementStorage(context: Context, dataMode: String): DepositMovementStorage {
		return DepositMovementStorage(context, namespaceSuffix(dataMode))
	}

	fun orderStorage(context: Context, dataMode: String): OrderStorage {
		return OrderStorage(context, namespaceSuffix(dataMode))
	}

	fun archivedDailyReportStorage(context: Context, dataMode: String): ArchivedDailyReportStorage {
		return ArchivedDailyReportStorage(context, namespaceSuffix(dataMode))
	}

	fun checkoutJournalStorage(context: Context, dataMode: String): CheckoutJournalStorage {
		return CheckoutJournalStorage(context, namespaceSuffix(dataMode))
	}

	fun articleStorage(context: Context, dataMode: String): ArticleStorage {
		return ArticleStorage(context, namespaceSuffix(dataMode))
	}

	fun globalSettingsStorage(context: Context, dataMode: String): GlobalSettingsStorage {
		val articleStorage = articleStorage(context, dataMode)
		return GlobalSettingsStorage(context, namespaceSuffix(dataMode), articleStorage)
	}

	private fun namespaceSuffix(dataMode: String): String {
		return if (AppDataMode.isDev(dataMode)) "dev" else "prod"
	}
}
