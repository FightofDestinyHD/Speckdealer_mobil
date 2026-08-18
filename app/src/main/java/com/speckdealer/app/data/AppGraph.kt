package com.speckdealer.app.data

import android.content.Context

object AppGraph {

	@Volatile
	private var productionRepository: ArticleRepository? = null
	@Volatile
	private var devRepository: ArticleRepository? = null

	fun repository(context: Context, dataMode: String = com.speckdealer.app.AppDataMode.MODE_PRODUCTION): ArticleRepository {
		if (com.speckdealer.app.AppDataMode.isDev(dataMode)) {
			return devRepository ?: synchronized(this) {
				devRepository ?: ArticleRepository(DataModeAwareStorageFactory.articleStorage(context, dataMode))
					.also { devRepository = it }
			}
		}
		return productionRepository ?: synchronized(this) {
			productionRepository ?: ArticleRepository(DataModeAwareStorageFactory.articleStorage(context, dataMode))
				.also { productionRepository = it }
		}
	}
}
