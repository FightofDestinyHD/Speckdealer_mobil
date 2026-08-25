package com.speckdealer.app.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
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

	@ColumnInfo(name = "wine_glass_deposit_enabled")
	public boolean wineGlassDepositEnabled;

	@ColumnInfo(name = "wine_bottle_deposit_enabled")
	public boolean wineBottleDepositEnabled;

	@ColumnInfo(name = "glass01_price_cents")
	public int glass01PriceCents;

	@ColumnInfo(name = "glass02_price_cents")
	public int glass02PriceCents;

	@ColumnInfo(name = "has_large_option")
	public boolean hasLargeOption;

	@ColumnInfo(name = "has_small_option")
	public boolean hasSmallOption;

	@ColumnInfo(name = "large_price_cents")
	public int largePriceCents;

	@ColumnInfo(name = "small_price_cents")
	public int smallPriceCents;

	@Ignore
	public ArticleEntity(String name, String category, int priceCents, String imageUri, boolean isWein,
						boolean hasBottleOption, boolean hasGlass01Option, boolean hasGlass02Option,
						boolean depositApplicable, boolean glassDepositOptional,
						int glass01PriceCents, int glass02PriceCents,
						boolean hasLargeOption, boolean hasSmallOption,
						int largePriceCents, int smallPriceCents) {
		this(
			name,
			category,
			priceCents,
			imageUri,
			isWein,
			hasBottleOption,
			hasGlass01Option,
			hasGlass02Option,
			depositApplicable,
			glassDepositOptional,
			isWein && (hasGlass01Option || hasGlass02Option || glassDepositOptional),
			isWein && hasBottleOption,
			glass01PriceCents,
			glass02PriceCents,
			hasLargeOption,
			hasSmallOption,
			largePriceCents,
			smallPriceCents
		);
	}

	public ArticleEntity(String name, String category, int priceCents, String imageUri, boolean isWein,
						boolean hasBottleOption, boolean hasGlass01Option, boolean hasGlass02Option,
						boolean depositApplicable, boolean glassDepositOptional,
						boolean wineGlassDepositEnabled, boolean wineBottleDepositEnabled,
						int glass01PriceCents, int glass02PriceCents,
						boolean hasLargeOption, boolean hasSmallOption,
						int largePriceCents, int smallPriceCents) {
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
		this.wineGlassDepositEnabled = wineGlassDepositEnabled;
		this.wineBottleDepositEnabled = wineBottleDepositEnabled;
		this.glass01PriceCents = glass01PriceCents;
		this.glass02PriceCents = glass02PriceCents;
		this.hasLargeOption = hasLargeOption;
		this.hasSmallOption = hasSmallOption;
		this.largePriceCents = largePriceCents;
		this.smallPriceCents = smallPriceCents;
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
			obj.put("wineGlassDepositEnabled", wineGlassDepositEnabled);
			obj.put("wineBottleDepositEnabled", wineBottleDepositEnabled);
				obj.put("glass01PriceCents", glass01PriceCents);
				obj.put("glass02PriceCents", glass02PriceCents);
				obj.put("hasLargeOption", hasLargeOption);
				obj.put("hasSmallOption", hasSmallOption);
				obj.put("largePriceCents", largePriceCents);
				obj.put("smallPriceCents", smallPriceCents);
			return obj;
		} catch (Exception e) {
			e.printStackTrace();
			return new JSONObject();
		}
	}

	public static ArticleEntity fromJson(JSONObject obj) {
		try {
			boolean legacyIsWein = obj.optBoolean("isWein", false);
			boolean legacyHasBottleOption = obj.optBoolean("hasBottleOption", false);
			boolean legacyHasGlass01Option = obj.optBoolean("hasGlass01Option", false);
			boolean legacyHasGlass02Option = obj.optBoolean("hasGlass02Option", false);
			boolean legacyGlassDepositOptional = obj.optBoolean("glassDepositOptional", false);
			boolean legacyDepositApplicable = obj.optBoolean("depositApplicable", false);
			boolean wineGlassDepositEnabled = obj.has("wineGlassDepositEnabled")
				? obj.optBoolean("wineGlassDepositEnabled", false)
				: (legacyIsWein && (legacyHasGlass01Option || legacyHasGlass02Option || legacyGlassDepositOptional));
			boolean wineBottleDepositEnabled = obj.has("wineBottleDepositEnabled")
				? obj.optBoolean("wineBottleDepositEnabled", false)
				: (legacyIsWein && (legacyHasBottleOption || (legacyDepositApplicable && !legacyGlassDepositOptional)));
			if (!obj.has("wineGlassDepositEnabled") && !obj.has("wineBottleDepositEnabled") && legacyIsWein && legacyDepositApplicable && legacyGlassDepositOptional) {
				wineGlassDepositEnabled = true;
			}

			ArticleEntity article = new ArticleEntity(
				obj.getString("name"),
				obj.getString("category"),
				obj.getInt("priceCents"),
				obj.optString("imageUri", null),
				legacyIsWein,
				legacyHasBottleOption,
				legacyHasGlass01Option,
				legacyHasGlass02Option,
				legacyDepositApplicable,
				legacyGlassDepositOptional,
				wineGlassDepositEnabled,
				wineBottleDepositEnabled,
				obj.optInt("glass01PriceCents", obj.optInt("priceCents", 0)),
				obj.optInt("glass02PriceCents", obj.optInt("priceCents", 0)),
				obj.optBoolean("hasLargeOption", false),
				obj.optBoolean("hasSmallOption", false),
				obj.optInt("largePriceCents", 0),
				obj.optInt("smallPriceCents", 0)
			);
			article.id = obj.getLong("id");
			return article;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
