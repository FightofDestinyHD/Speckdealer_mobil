package com.speckdealer.app

import com.speckdealer.app.data.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Test

class TaxCalculatorTest {

	@Test
	fun beverage_19_percent_isCalculated() {
		val breakdown = TaxCalculator.calculateFromGross(1190, TaxCategory.BEVERAGE)
		assertEquals(1000, breakdown.netAmountCents)
		assertEquals(190, breakdown.taxAmountCents)
		assertEquals(1190, breakdown.grossAmountCents)
	}

	@Test
	fun snack_7_percent_isCalculated() {
		val breakdown = TaxCalculator.calculateFromGross(1070, TaxCategory.FOOD)
		assertEquals(1000, breakdown.netAmountCents)
		assertEquals(70, breakdown.taxAmountCents)
		assertEquals(1070, breakdown.grossAmountCents)
	}

	@Test
	fun kaese_and_speck_are_food_7_percent() {
		assertEquals(TaxCategory.FOOD, TaxPolicy.resolveTaxCategory(CategoryType.KAESE.storageValue, null))
		assertEquals(TaxCategory.FOOD, TaxPolicy.resolveTaxCategory(CategoryType.SPECK.storageValue, null))
	}

	@Test
	fun pfand_hasNoTax() {
		val taxCategory = TaxPolicy.resolveTaxCategory(CategoryType.PFAND.storageValue, null)
		assertEquals(TaxCategory.DEPOSIT, taxCategory)
		val breakdown = TaxCalculator.calculateFromGross(200, taxCategory)
		assertEquals(200, breakdown.netAmountCents)
		assertEquals(0, breakdown.taxAmountCents)
	}

	@Test
	fun angebot_requiresExplicitTaxCategory() {
		var thrown = false
		try {
			TaxPolicy.resolveTaxCategory(CategoryType.ANGEBOT.storageValue, null)
		} catch (_: IllegalArgumentException) {
			thrown = true
		}
		assertEquals(true, thrown)
	}

	@Test
	fun angebot_withExplicitTaxCategory_isAccepted() {
		assertEquals(
			TaxCategory.BEVERAGE,
			TaxPolicy.resolveTaxCategory(CategoryType.ANGEBOT.storageValue, TaxCategory.BEVERAGE)
		)
	}

	@Test
	fun smallCents_roundingIsStable() {
		val breakdown = TaxCalculator.calculateFromGross(1, TaxCategory.FOOD)
		assertEquals(1, breakdown.netAmountCents + breakdown.taxAmountCents)
	}

	@Test
	fun netPlusTax_equalsGross() {
		val samples = listOf(1, 2, 7, 19, 99, 199, 999, 1190)
		samples.forEach { gross ->
			val b = TaxCalculator.calculateFromGross(gross, TaxCategory.BEVERAGE)
			assertEquals(gross, b.netAmountCents + b.taxAmountCents)
		}
	}
}
