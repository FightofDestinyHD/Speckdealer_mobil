package com.speckdealer.app.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

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

	public ArticleEntity(String name, String category, int priceCents, String imageUri, boolean isWein,
						 boolean hasBottleOption, boolean hasGlass01Option, boolean hasGlass02Option,
						 boolean depositApplicable, boolean glassDepositOptional) {
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
	}
}
