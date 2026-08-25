package com.speckdealer.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {ArticleEntity.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

	private static volatile AppDatabase INSTANCE;

	private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
		@Override
		public void migrate(SupportSQLiteDatabase database) {
			database.execSQL("ALTER TABLE articles ADD COLUMN wine_glass_deposit_enabled INTEGER NOT NULL DEFAULT 0");
			database.execSQL("ALTER TABLE articles ADD COLUMN wine_bottle_deposit_enabled INTEGER NOT NULL DEFAULT 0");
			database.execSQL(
				"UPDATE articles SET wine_glass_deposit_enabled = CASE WHEN is_wein = 1 AND (has_glass_01_option = 1 OR has_glass_02_option = 1 OR glass_deposit_optional = 1) THEN 1 ELSE 0 END"
			);
			database.execSQL(
				"UPDATE articles SET wine_bottle_deposit_enabled = CASE WHEN is_wein = 1 AND has_bottle_option = 1 THEN 1 ELSE 0 END"
			);
		}
	};

	public abstract ArticleDao articleDao();

	public static AppDatabase getInstance(Context context) {
		if (INSTANCE == null) {
			synchronized (AppDatabase.class) {
				if (INSTANCE == null) {
					INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "speckdealer.db")
						.addMigrations(MIGRATION_1_2)
						.build();
				}
			}
		}
		return INSTANCE;
	}
}
