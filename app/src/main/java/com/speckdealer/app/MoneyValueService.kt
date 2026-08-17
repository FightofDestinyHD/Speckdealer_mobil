package com.speckdealer.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object MoneyValueService {

	const val MAX_ALLOWED_CENTS: Long = 999_999_999L // 9.999.999,99 €

	enum class ParseError {
		EMPTY,
		INVALID_FORMAT,
		NEGATIVE,
		ZERO_OR_NEGATIVE,
		TOO_LARGE
	}

	data class ParseResult(
		val cents: Long? = null,
		val error: ParseError? = null
	) {
		val isValid: Boolean get() = cents != null && error == null
	}

	fun parseAmountToCents(rawInput: String, allowZero: Boolean = false): ParseResult {
		val normalized = rawInput.trim()
		if (normalized.isEmpty()) return ParseResult(error = ParseError.EMPTY)

		val unified = normalized.replace(',', '.')
		val validFormat = Regex("^\\d+(?:\\.\\d{1,2})?$").matches(unified)
		if (!validFormat) return ParseResult(error = ParseError.INVALID_FORMAT)

		val decimal = try {
			BigDecimal(unified)
		} catch (_: NumberFormatException) {
			return ParseResult(error = ParseError.INVALID_FORMAT)
		}

		if (decimal < BigDecimal.ZERO) return ParseResult(error = ParseError.NEGATIVE)

		val cents = try {
			decimal
				.multiply(BigDecimal(100))
				.setScale(0, RoundingMode.UNNECESSARY)
				.longValueExact()
		} catch (_: Exception) {
			return ParseResult(error = ParseError.INVALID_FORMAT)
		}

		if (!allowZero && cents <= 0L) return ParseResult(error = ParseError.ZERO_OR_NEGATIVE)
		if (cents > MAX_ALLOWED_CENTS) return ParseResult(error = ParseError.TOO_LARGE)

		return ParseResult(cents = cents)
	}

	fun allocateProportionally(weights: List<Long>, totalCents: Long): List<Long> {
		require(totalCents >= 0L) { "totalCents must be >= 0" }
		if (weights.isEmpty()) return emptyList()
		if (weights.any { it < 0L }) throw IllegalArgumentException("weights must be >= 0")

		val weightSum = weights.sum().coerceAtLeast(1L)
		val base = MutableList(weights.size) { 0L }
		val remainders = mutableListOf<Pair<Int, Long>>()
		var distributed = 0L

		weights.forEachIndexed { idx, weight ->
			val numerator = weight * totalCents
			val share = numerator / weightSum
			val rest = numerator % weightSum
			base[idx] = share
			remainders += idx to rest
			distributed += share
		}

		var missing = totalCents - distributed
		if (missing > 0) {
			val sorted = remainders.sortedWith(
				compareByDescending<Pair<Int, Long>> { it.second }.thenBy { it.first }
			)
			var cursor = 0
			while (missing > 0) {
				val idx = sorted[cursor % sorted.size].first
				base[idx] = base[idx] + 1L
				missing--
				cursor++
			}
		}

		return base
	}

	fun formatCents(cents: Long, locale: Locale = Locale.GERMANY): String {
		return NumberFormat.getCurrencyInstance(locale).format(cents / 100.0)
	}
}
