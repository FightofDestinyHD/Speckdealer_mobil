package com.speckdealer.app.data

data class GlobalDepositSettings(
	val glassDepositCents: Int,
	val bottleDepositCents: Int,
	val plateDepositCents: Int
) {
	fun amountForToken(token: String?): Int {
		return when (token?.trim()?.lowercase()) {
			"glas", "glass", "glass_01", "glass_02" -> glassDepositCents
			"flasche", "bottle" -> bottleDepositCents
			"teller", "plate" -> plateDepositCents
			else -> 0
		}
	}
}
