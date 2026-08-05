package com.speckdealer.app.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import org.json.JSONObject;

@Entity(tableName = "articles")
public class ArticleEntity {
	@PrimaryKey(autoGenerate = true)
	public long id;

	@ColumnInfo(name = "name")
	public String name;

	@ColumnInfo(name = "category")
	public String category;

	@ColumnInfo(name = "price_cents")
	public int priceCents;

	@ColumnInfo(name = "image_uri")
	public String imageUri;

	@ColumnInfo(name = "is_wein")
	public boolean isWein;

	@ColumnInfo(name = "has_bottle_option")
	public boolean hasBottleOption;

	@ColumnInfo(name = "has_glass_01_option")
	public boolean hasGlass01Option;

	@ColumnInfo(name = "has_glass_02_option")
	public boolean hasGlass02Option;

	@ColumnInfo(name = "deposit_applicable")
	public boolean depositApplicable;

	@ColumnInfo(name = "glass_deposit_optional")
	public boolean glassDepositOptional;

	@ColumnInfo(name = "glass01_price_cents")
	public int glass01PriceCents;

	@ColumnInfo(name = "glass02_price_cents")
	public int glass02PriceCents;

	public ArticleEntity(String name, String category, int priceCents, String imageUri, boolean isWein,
						boolean hasBottleOption, boolean hasGlass01Option, boolean hasGlass02Option,
						boolean depositApplicable, boolean glassDepositOptional,
						int glass01PriceCents, int glass02PriceCents) {
		this.name = name;
		this.category = category;
		this.priceCents = priceCents;
		this.imageUri = imageUri;
		this.isWein = isWein;
		this.hasBottleOption = hasBottleOption;
		this.hasGlass01Option = hasGlass01Option;
		this.hasGlass02Option = hasGlass02Option;
		this.depositApplicable = depositApplicable;
		this.glassDepositOptional = glassDepositOptional;
		this.glass01PriceCents = glass01PriceCents;
		this.glass02PriceCents = glass02PriceCents;
	}

	public JSONObject toJson() {
		try {
			JSONObject obj = new JSONObject();
			obj.put("id", id);
			obj.put("name", name);
			obj.put("category", category);
			obj.put("priceCents", priceCents);
			obj.put("imageUri", imageUri);
			obj.put("isWein", isWein);
			obj.put("hasBottleOption", hasBottleOption);
			obj.put("hasGlass01Option", hasGlass01Option);
			obj.put("hasGlass02Option", hasGlass02Option);
			obj.put("depositApplicable", depositApplicable);
			obj.put("glassDepositOptional", glassDepositOptional);
			obj.put("glass01PriceCents", glass01PriceCents);
			obj.put("glass02PriceCents", glass02PriceCents);
			return obj;
		} catch (Exception e) {
			e.printStackTrace();
			return new JSONObject();
		}
	}

	public static ArticleEntity fromJson(JSONObject obj) {
		try {
			ArticleEntity article = new ArticleEntity(
				obj.getString("name"),
				obj.getString("category"),
				obj.getInt("priceCents"),
				obj.optString("imageUri", null),
				obj.getBoolean("isWein"),
				obj.getBoolean("hasBottleOption"),
				obj.getBoolean("hasGlass01Option"),
				obj.getBoolean("hasGlass02Option"),
				obj.getBoolean("depositApplicable"),
				obj.getBoolean("glassDepositOptional"),
				obj.optInt("glass01PriceCents", obj.optInt("priceCents", 0)),
				obj.optInt("glass02PriceCents", obj.optInt("priceCents", 0))
			);
			article.id = obj.getLong("id");
			return article;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
