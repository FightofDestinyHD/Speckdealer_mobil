package com.speckdealer.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class ArticleStorage(private val context: Context) {

	private val prefs: SharedPreferences = context.getSharedPreferences("speckdealer_articles", Context.MODE_PRIVATE)
	private val articlesFlow = MutableStateFlow<List<ArticleEntity>>(emptyList())

	init {
		loadArticles()
	}

	private fun loadArticles() {
		val json = prefs.getString("articles", "[]") ?: "[]"
		try {
			val articles = mutableListOf<ArticleEntity>()
			val jsonArray = JSONArray(json)
			for (i in 0 until jsonArray.length()) {
				val obj = jsonArray.getJSONObject(i)
				articles.add(ArticleEntity.fromJson(obj))
			}
			articlesFlow.value = articles.sortedBy { it.id }
		} catch (e: Exception) {
			e.printStackTrace()
			articlesFlow.value = emptyList()
		}
	}

	private fun saveArticles() {
		try {
			val jsonArray = JSONArray()
			for (article in articlesFlow.value) {
				jsonArray.put(article.toJson())
			}
			prefs.edit().putString("articles", jsonArray.toString()).apply()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	fun observeArticles(): Flow<List<ArticleEntity>> = articlesFlow

	fun observeArticlesByCategory(category: CategoryType): Flow<List<ArticleEntity>> {
		return articlesFlow.map { articles ->
			articles.filter { it.category == category.getStorageValue() }
		}
	}

	fun getArticles(): List<ArticleEntity> = articlesFlow.value

	fun getArticlesByCategory(category: CategoryType): List<ArticleEntity> {
		return articlesFlow.value.filter { it.category == category.getStorageValue() }
	}

	fun getDepositArticles(): List<ArticleEntity> {
		return articlesFlow.value.filter { it.category == CategoryType.PFAND.storageValue }
	}

	fun getDepositArticleForType(typeToken: String): ArticleEntity? {
		return getDepositArticles().firstOrNull { article ->
			article.name.lowercase().contains(typeToken.lowercase())
		}
	}

	fun saveArticle(article: ArticleEntity) {
		try {
			val current = articlesFlow.value.toMutableList()
			// Auto-ID generieren wenn nicht vorhanden
			if (article.id == 0L) {
				article.id = (current.maxOfOrNull { it.id } ?: 0L) + 1
			}
			current.add(article)
			articlesFlow.value = current
			saveArticles()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	fun updateArticle(article: ArticleEntity) {
		try {
			val current = articlesFlow.value.toMutableList()
			val index = current.indexOfFirst { it.id == article.id }
			if (index >= 0) {
				current[index] = article
				articlesFlow.value = current
				saveArticles()
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	fun deleteArticle(id: Long) {
		try {
			val current = articlesFlow.value.toMutableList()
			current.removeAll { it.id == id }
			articlesFlow.value = current
			saveArticles()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
}
