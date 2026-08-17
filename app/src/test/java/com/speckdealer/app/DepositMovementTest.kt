package com.speckdealer.app

import com.speckdealer.app.data.DepositMovement
import com.speckdealer.app.data.DepositMovementType
import org.junit.Test

class DepositMovementTest {

	@Test(expected = IllegalArgumentException::class)
	fun constructor_rejectsBlankDisplayName() {
		DepositMovement(
			transactionId = "t2",
			depositType = "BOTTLE",
			displayName = "",
			quantity = 1,
			unitAmountCents = 100,
			totalAmountCents = 100,
			movementType = DepositMovementType.RETURNED
		)
	}
}
