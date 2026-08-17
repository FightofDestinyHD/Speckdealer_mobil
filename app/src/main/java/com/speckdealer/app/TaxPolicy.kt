package com.speckdealer.app

import com.speckdealer.app.data.CategoryType

enum class TaxCategory {
	BEVERAGE,
	FOOD,
	DEPOSIT
}

object TaxPolicy {
	const val BEVERAGE_RATE_BASIS_POINTS = 1900
	const val FOOD_RATE_BASIS_POINTS = 700

	fun rateBasisPoints(category: TaxCategory): Int = when (category) {
		TaxCategory.BEVERAGE -> BEVERAGE_RATE_BASIS_POINTS
		TaxCategory.FOOD -> FOOD_RATE_BASIS_POINTS
		TaxCategory.DEPOSIT -> 0
	}

	fun resolveTaxCategory(categoryStorageValue: String, explicitOfferTaxCategory: TaxCategory?): TaxCategory {
		return when (CategoryType.fromStorageValue(categoryStorageValue)) {
			CategoryType.WEIN,
			CategoryType.SOFTGETRAENKE -> TaxCategory.BEVERAGE
			CategoryType.SNACKS,
			CategoryType.KAESE,
			CategoryType.SPECK -> TaxCategory.FOOD
			CategoryType.PFAND -> TaxCategory.DEPOSIT
			CategoryType.ANGEBOT -> explicitOfferTaxCategory
				?: throw IllegalArgumentException("Angebot benötigt eine explizite Steuerkategorie")
		}
	}
}

data class TaxBreakdown(
	val netAmountCents: Int,
	val taxAmountCents: Int,
	val grossAmountCents: Int,
	val taxRateBasisPoints: Int
)

object TaxCalculator {
	fun calculateFromGross(grossAmountCents: Int, taxCategory: TaxCategory): TaxBreakdown {
		val gross = grossAmountCents.coerceAtLeast(0)
		val rate = TaxPolicy.rateBasisPoints(taxCategory)
		if (rate <= 0 || gross <= 0) {
			return TaxBreakdown(
				netAmountCents = gross,
				taxAmountCents = 0,
				grossAmountCents = gross,
				taxRateBasisPoints = 0
			)
		}
		val denominator = 10_000L + rate.toLong()
		val net = ((gross.toLong() * 10_000L) + (denominator / 2L)) / denominator
		val netInt = net.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
		val tax = gross - netInt
		return TaxBreakdown(
			netAmountCents = netInt,
			taxAmountCents = tax,
			grossAmountCents = gross,
			taxRateBasisPoints = rate
		)
	}
}
