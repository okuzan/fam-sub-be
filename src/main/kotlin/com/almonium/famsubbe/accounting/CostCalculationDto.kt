package com.almonium.famsubbe.accounting

import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

data class CostCalculationRequest(
    val fromMonth: YearMonth,
    val toMonth: YearMonth
)

data class CostCalculationResult(
    val batchId: UUID?,
    val fromMonth: YearMonth,
    val toMonth: YearMonth,
    val createdAt: Instant,
    val createdByAccountId: UUID,
    val monthsProcessed: Int,
    val chargesProcessed: Int,
    val chargesSkipped: Int,
    val ledgerEntriesCreated: Int,
    val items: List<CostCalculationItemResult>
)

data class CostCalculationItemResult(
    val chargeId: UUID,
    val serviceId: UUID,
    val serviceName: String,
    val chargeAmount: BigDecimal,
    val participantCount: Int,
    val success: Boolean,
    val message: String
)
