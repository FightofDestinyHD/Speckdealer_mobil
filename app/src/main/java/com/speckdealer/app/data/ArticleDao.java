package com.speckdealer.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import kotlinx.coroutines.flow.Flow;

@Dao
public interface ArticleDao {
	@Query("SELECT * FROM articles ORDER BY category, name")
	Flow<List<ArticleEntity>> observeAll();

	@Query("SELECT * FROM articles WHERE category = :category ORDER BY name")
	Flow<List<ArticleEntity>> observeByCategory(String category);

	@Query("SELECT * FROM articles WHERE category = :category ORDER BY name")
	List<ArticleEntity> getByCategory(String category);

	@Query("SELECT * FROM articles WHERE category = 'PFAND' ORDER BY name")
	List<ArticleEntity> getDepositArticles();

	@Query("SELECT * FROM articles WHERE category = 'PFAND' AND lower(name) LIKE '%' || lower(:typeToken) || '%' ORDER BY name LIMIT 1")
	ArticleEntity findDepositArticleByTypeToken(String typeToken);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	long insert(ArticleEntity article);

	@Update
	void update(ArticleEntity article);

	@Delete
	void delete(ArticleEntity article);
}
