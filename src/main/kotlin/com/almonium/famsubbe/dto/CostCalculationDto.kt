package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class CostCalculationResult(
    val calculationBatchId: UUID,
    val targetMonth: YearMonth,
    val calculatedAt: Instant,
    val createdByAccountId: UUID,
    val chargesProcessed: Int,
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
