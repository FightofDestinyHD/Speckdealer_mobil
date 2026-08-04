package com.speckdealer.app.data;

import java.util.List;

import kotlinx.coroutines.flow.Flow;

public class ArticleRepository {
	private final ArticleDao articleDao;

	public ArticleRepository(ArticleDao articleDao) {
		this.articleDao = articleDao;
	}

	public Flow<List<ArticleEntity>> observeAllArticles() {
		return articleDao.observeAll();
	}

	public Flow<List<ArticleEntity>> observeArticlesByCategory(CategoryType categoryType) {
		return articleDao.observeByCategory(categoryType.getStorageValue());
	}

	public List<ArticleEntity> getArticlesByCategory(CategoryType categoryType) {
		return articleDao.getByCategory(categoryType.getStorageValue());
	}

	public List<ArticleEntity> getDepositArticles() {
		return articleDao.getDepositArticles();
	}

	public ArticleEntity getDepositArticleForType(String typeToken) {
		return articleDao.findDepositArticleByTypeToken(typeToken);
	}

	public long saveArticle(ArticleEntity article) {
		return articleDao.insert(article);
	}

	public void updateArticle(ArticleEntity article) {
		articleDao.update(article);
	}

	public void deleteArticle(ArticleEntity article) {
		articleDao.delete(article);
	}
}
