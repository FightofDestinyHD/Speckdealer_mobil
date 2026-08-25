package com.speckdealer.app.data;

import java.util.Arrays;
import java.util.List;

public enum CategoryType {
	WEIN("WEIN", "Wein"),
	SOFTGETRAENKE("SOFTGETRAENKE", "Softgetränke"),
	SPECK("SPECK", "Speck"),
	KAESE("KAESE", "Käse"),
	SNACKS("SNACKS", "Snacks"),
	ANGEBOT("ANGEBOT", "Angebot"),
	PFAND("PFAND", "Pfand");

	private final String storageValue;
	private final String displayName;

	CategoryType(String storageValue, String displayName) {
		this.storageValue = storageValue;
		this.displayName = displayName;
	}

	public String getStorageValue() {
		return storageValue;
	}

	public String getDisplayName() {
		return displayName;
	}

	public static CategoryType fromStorageValue(String value) {
		for (CategoryType type : values()) {
			if (type.storageValue.equals(value)) {
				return type;
			}
		}
		return WEIN;
	}

	public static List<CategoryType> defaultOrder() {
		return Arrays.asList(WEIN, SOFTGETRAENKE, SPECK, KAESE, SNACKS, ANGEBOT);
	}
}
