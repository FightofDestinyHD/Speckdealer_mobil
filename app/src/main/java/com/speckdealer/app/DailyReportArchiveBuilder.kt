package com.speckdealer.app

import com.speckdealer.app.data.ArchivedArticleSummary
import com.speckdealer.app.data.ArchivedDailyReport
import com.speckdealer.app.data.ArchivedDepositSummary
import com.speckdealer.app.data.ArchivedEmployeeSaleSummary
import com.speckdealer.app.data.ArchivedOrderSummary
import com.speckdealer.app.data.DepositMovement
import com.speckdealer.app.data.DepositMovementType
import com.speckdealer.app.data.OrderRecord
import com.speckdealer.app.data.SaleRecord

object DailyReportArchiveBuilder {

	const val SNAPSHOT_VERSION = 1

	fun buildCompletionTransactionId(
		businessDate: String,
		records: List<SaleRecord>,
		depositMovements: List<DepositMovement>,
		orders: List<OrderRecord>
	): String {
		val recordsTs = records.maxOfOrNull { it.timestampMs } ?: 0L
		val depositsTs = depositMovements.maxOfOrNull { it.timestampMs } ?: 0L
		val ordersTs = orders.maxOfOrNull { it.timestampMs } ?: 0L
		return listOf(
			"daily-close",
			businessDate,
			records.size,
			depositMovements.size,
			orders.size,
			recordsTs,
			depositsTs,
			ordersTs
		).joinToString("-")
	}

	fun buildArchivedReport(
		businessDate: String,
		archivedAt: Long,
		completionTransactionId: String,
		records: List<SaleRecord>,
		depositMovements: List<DepositMovement>,
		orders: List<OrderRecord>
	): ArchivedDailyReport {
		val taxableSales = records.filter { !it.isEmployee }
		val employeeSales = records.filter { it.isEmployee }

		val beverageSales = mutableListOf<SaleRecord>()
		val foodSales = mutableListOf<SaleRecord>()
		taxableSales.forEach { sale ->
			when (classifyTaxBucket(sale)) {
				TaxBucket.BEVERAGE -> beverageSales.add(sale)
				TaxBucket.FOOD -> foodSales.add(sale)
				null -> Unit
			}
		}

		val beverageNet = beverageSales.sumOf { it.netAmountCents.toLong() }
		val beverageVat = beverageSales.sumOf { it.taxAmountCents.toLong() }
		val beverageGross = beverageSales.sumOf { it.grossAmountCents.toLong() }
		val foodNet = foodSales.sumOf { it.netAmountCents.toLong() }
		val foodVat = foodSales.sumOf { it.taxAmountCents.toLong() }
		val foodGross = foodSales.sumOf { it.grossAmountCents.toLong() }
		val totalRevenue = taxableSales.sumOf { it.grossAmountCents.toLong() }

		val depositReceived = taxableSales.sumOf { it.depositCents.toLong() }
		val returnedMovements = depositMovements.filter { it.movementType == DepositMovementType.RETURNED }
		val depositReturned = returnedMovements.sumOf { it.totalAmountCents }
		val depositBalance = depositReceived - depositReturned

		val fixedDepositBreakdown = fixedDepositSummaries(returnedMovements)
		val additionalDepositBreakdown = additionalDepositSummaries(returnedMovements)

		val employeeSummaries = employeeSales
			.groupBy { it.articleName }
			.entries
			.sortedBy { it.key }
			.map { (name, list) -> ArchivedEmployeeSaleSummary(name, list.size) }

		val articleSummaries = taxableSales
			.groupBy { Triple(it.articleName, it.category, it.taxCategory) }
			.entries
			.sortedBy { it.key.first }
			.map { (key, list) ->
				ArchivedArticleSummary(
					articleName = key.first,
					category = key.second,
					taxCategory = key.third,
					count = list.size,
					grossCents = list.sumOf { it.grossAmountCents.toLong() }
				)
			}

		val orderSummaries = orders
			.sortedBy { it.timestampMs }
			.map { order ->
				ArchivedOrderSummary(
					id = order.id,
					articleName = order.articleName,
					sizeName = order.sizeName,
					totalCents = order.priceCents.toLong() + order.depositCents.toLong(),
					isEmployee = order.isEmployee,
					detailsText = order.buildDetailsText()
				)
			}

		return ArchivedDailyReport(
			completionTransactionId = completionTransactionId,
			businessDate = businessDate,
			archivedAt = archivedAt,
			totalRevenueCents = totalRevenue,
			beverageNetCents = beverageNet,
			beverageVatCents = beverageVat,
			beverageGrossCents = beverageGross,
			foodNetCents = foodNet,
			foodVatCents = foodVat,
			foodGrossCents = foodGross,
			depositReceivedCents = depositReceived,
			depositReturnedCents = depositReturned,
			depositBalanceCents = depositBalance,
			salesCount = taxableSales.size,
			orderCount = orders.size,
			employeeSales = employeeSummaries,
			articleSummaries = articleSummaries,
			depositSummaries = fixedDepositBreakdown + additionalDepositBreakdown,
			orderSummaries = orderSummaries,
			sourceSnapshotVersion = SNAPSHOT_VERSION
		)
	}

	private enum class TaxBucket {
		BEVERAGE,
		FOOD
	}

	private fun classifyTaxBucket(record: SaleRecord): TaxBucket? {
		val taxCategory = record.taxCategory.trim().uppercase()
		if (taxCategory == TaxCategory.BEVERAGE.name) return TaxBucket.BEVERAGE
		if (taxCategory == TaxCategory.FOOD.name) return TaxBucket.FOOD
		if (taxCategory == TaxCategory.DEPOSIT.name) return null

		return when (record.category.trim().uppercase()) {
			"WEIN", "SOFTGETRAENKE" -> TaxBucket.BEVERAGE
			"SNACKS", "KAESE", "SPECK" -> TaxBucket.FOOD
			else -> null
		}
	}

	private fun fixedDepositSummaries(returnedMovements: List<DepositMovement>): List<ArchivedDepositSummary> {
		val bottleMovements = returnedMovements.filter { isBottleType(it.depositType, it.displayName) }
		val glassMovements = returnedMovements.filter { isGlassType(it.depositType, it.displayName) }
		val plateMovements = returnedMovements.filter { isPlateType(it.depositType, it.displayName) }
		return listOf(
			ArchivedDepositSummary(
				depositType = "BOTTLE",
				displayName = "Flasche",
				quantity = bottleMovements.sumOf { it.quantity },
				amountCents = bottleMovements.sumOf { it.totalAmountCents }
			),
			ArchivedDepositSummary(
				depositType = "GLASS",
				displayName = "Glas",
				quantity = glassMovements.sumOf { it.quantity },
				amountCents = glassMovements.sumOf { it.totalAmountCents }
			),
			ArchivedDepositSummary(
				depositType = "PLATE",
				displayName = "Teller",
				quantity = plateMovements.sumOf { it.quantity },
				amountCents = plateMovements.sumOf { it.totalAmountCents }
			)
		)
	}

	private fun additionalDepositSummaries(returnedMovements: List<DepositMovement>): List<ArchivedDepositSummary> {
		return returnedMovements
			.filterNot {
				isBottleType(it.depositType, it.displayName) ||
					isGlassType(it.depositType, it.displayName) ||
					isPlateType(it.depositType, it.displayName)
			}
			.groupBy { it.depositType.ifBlank { it.displayName } }
			.entries
			.sortedBy { it.key }
			.map { (type, list) ->
				ArchivedDepositSummary(
					depositType = type,
					displayName = list.firstOrNull()?.displayName ?: type,
					quantity = list.sumOf { it.quantity },
					amountCents = list.sumOf { it.totalAmountCents }
				)
			}
	}

	private fun isBottleType(type: String, displayName: String): Boolean {
		val normalizedType = type.trim().uppercase()
		val normalizedName = displayName.trim().uppercase()
		return normalizedType == "BOTTLE" || normalizedType == "FLASCHE" || normalizedName == "FLASCHE"
	}

	private fun isGlassType(type: String, displayName: String): Boolean {
		val normalizedType = type.trim().uppercase()
		val normalizedName = displayName.trim().uppercase()
		return normalizedType == "GLASS" ||
			normalizedType == "GLAS" ||
			normalizedType == "GLASS_01" ||
			normalizedType == "GLASS_02" ||
			normalizedType == "GLAS_01" ||
			normalizedType == "GLAS_02" ||
			normalizedName == "GLAS"
	}

	private fun isPlateType(type: String, displayName: String): Boolean {
		val normalizedType = type.trim().uppercase()
		val normalizedName = displayName.trim().uppercase()
		return normalizedType == "PLATE" || normalizedType == "TELLER" || normalizedName == "TELLER"
	}
}
