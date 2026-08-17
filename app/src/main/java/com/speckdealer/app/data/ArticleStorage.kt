package com.speckdealer.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

class ArticleStorage(context: Context) {

	private val prefs: SharedPreferences = context.getSharedPreferences("speckdealer_articles", Context.MODE_PRIVATE)
	private val store = SafeJsonArrayPreferencesStore(
		prefs = prefs,
		key = "articles",
		lockName = "speckdealer_articles:articles"
	)
	private val lock = Any()
	private val articlesFlow = MutableStateFlow<List<ArticleEntity>>(emptyList())

	init {
		loadArticles()
	}

	private fun loadArticles() {
		synchronized(lock) {
			val read = store.readArray()
			articlesFlow.value = parseArticles(read.array)
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
		val normalizedToken = typeToken.trim().lowercase()
		if (normalizedToken.isBlank()) return null
		val deposits = getDepositArticles()
		val direct = deposits.firstOrNull { article ->
			resolveDepositType(article) == normalizedToken
		}
		if (direct != null) return direct
		return deposits.firstOrNull { article ->
			article.name.lowercase().contains(normalizedToken)
		}
	}

	fun resolveDepositType(article: ArticleEntity): String {
		val normalizedName = article.name.lowercase()
		return when {
			normalizedName.contains("teller") -> "teller"
			normalizedName.contains("0,1") || normalizedName.contains("0.1") -> "glass_01"
			normalizedName.contains("0,2") || normalizedName.contains("0.2") -> "glass_02"
			normalizedName.contains("glas") -> "glas"
			normalizedName.contains("flasche") || normalizedName.contains("bottle") -> "bottle"
			else -> "unknown"
		}
	}

	fun saveArticle(article: ArticleEntity) {
		synchronized(lock) {
			val (writeResult, persisted) = store.updateArray { currentArray ->
				val current = parseArticles(currentArray).toMutableList()
				if (article.id == 0L || current.any { it.id == article.id }) {
					article.id = (current.maxOfOrNull { it.id } ?: 0L) + 1
				}
				current.add(article)
				val normalized = normalize(current)
				toJsonArray(normalized) to normalized
			}
			if (!writeResult.success) {
				throw IllegalStateException(writeResult.errorMessage ?: "Artikel konnten nicht gespeichert werden")
			}
			articlesFlow.value = persisted
		}
	}

	fun updateArticle(article: ArticleEntity) {
		synchronized(lock) {
			val (writeResult, persisted) = store.updateArray { currentArray ->
				val current = parseArticles(currentArray).toMutableList()
				val index = current.indexOfFirst { it.id == article.id }
				if (index >= 0) {
					current[index] = article
				}
				val normalized = normalize(current)
				toJsonArray(normalized) to normalized
			}
			if (!writeResult.success) {
				throw IllegalStateException(writeResult.errorMessage ?: "Artikel konnten nicht aktualisiert werden")
			}
			articlesFlow.value = persisted
		}
	}

	fun deleteArticle(id: Long) {
		synchronized(lock) {
			val (writeResult, persisted) = store.updateArray { currentArray ->
				val current = parseArticles(currentArray).filter { it.id != id }
				val normalized = normalize(current)
				toJsonArray(normalized) to normalized
			}
			if (!writeResult.success) {
				throw IllegalStateException(writeResult.errorMessage ?: "Artikel konnten nicht gelöscht werden")
			}
			articlesFlow.value = persisted
		}
	}

	private fun parseArticles(array: JSONArray): List<ArticleEntity> {
		val parsed = mutableListOf<ArticleEntity>()
		for (i in 0 until array.length()) {
			val article = runCatching {
				ArticleEntity.fromJson(array.getJSONObject(i))
			}.getOrNull()
			if (article != null) {
				parsed.add(article)
			}
		}
		return normalize(parsed)
	}

	private fun normalize(items: List<ArticleEntity>): List<ArticleEntity> {
		return items
			.distinctBy { it.id }
			.sortedBy { it.id }
	}

	private fun toJsonArray(items: List<ArticleEntity>): JSONArray {
		val arr = JSONArray()
		items.forEach { arr.put(it.toJson()) }
		return arr
	}
}
