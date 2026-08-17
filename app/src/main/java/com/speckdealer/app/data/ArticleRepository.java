package com.speckdealer.app.data;

import java.util.List;

import kotlinx.coroutines.flow.Flow;

public class ArticleRepository {
	private final ArticleStorage storage;

	public ArticleRepository(ArticleStorage storage) {
		this.storage = storage;
	}

	public Flow<List<ArticleEntity>> observeAllArticles() {
		return storage.observeArticles();
	}

	public Flow<List<ArticleEntity>> observeArticlesByCategory(CategoryType categoryType) {
		return storage.observeArticlesByCategory(categoryType);
	}

	public List<ArticleEntity> getArticlesByCategory(CategoryType categoryType) {
		return storage.getArticlesByCategory(categoryType);
	}

	public List<ArticleEntity> getDepositArticles() {
		return storage.getDepositArticles();
	}

	public ArticleEntity getDepositArticleForType(String typeToken) {
		try {
			return storage.getDepositArticleForType(typeToken);
		} catch (Exception e) {
			return null;
		}
	}

	public String resolveDepositType(ArticleEntity article) {
		return storage.resolveDepositType(article);
	}

	public long saveArticle(ArticleEntity article) {
		storage.saveArticle(article);
		return article.id;
	}

	public void updateArticle(ArticleEntity article) {
		storage.updateArticle(article);
	}

	public void deleteArticle(ArticleEntity article) {
		storage.deleteArticle(article.id);
	}
}
