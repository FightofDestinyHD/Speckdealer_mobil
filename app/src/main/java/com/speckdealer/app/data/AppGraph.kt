package com.speckdealer.app.data

import android.content.Context

object AppGraph {

	@Volatile
	private var repository: ArticleRepository? = null

	fun repository(context: Context): ArticleRepository {
		return repository ?: synchronized(this) {
			repository ?: ArticleRepository(ArticleStorage(context))
				.also { repository = it }
		}
	}
}
