package com.speckdealer.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object AppGraph {

	@Volatile
	private var repository: ArticleRepository? = null

	@Volatile
	private var database: AppDatabase? = null
	private var isInitializing = false

	fun repository(context: Context): ArticleRepository {
		return repository ?: synchronized(this) {
			repository ?: ArticleRepository(getOrCreateDatabase(context).articleDao())
				.also { repository = it }
		}
	}

	private fun getOrCreateDatabase(context: Context): AppDatabase {
		return database ?: synchronized(this) {
			database ?: AppDatabase.getInstance(context).also { database = it }
		}
	}

	fun initializeDatabaseAsync(context: Context) {
		if (isInitializing || database != null || repository != null) return
		isInitializing = true

		GlobalScope.launch(Dispatchers.IO) {
			try {
				getOrCreateDatabase(context)
				repository(context)
				isInitializing = false
			} catch (e: Exception) {
				e.printStackTrace()
				isInitializing = false
			}
		}
	}
}
