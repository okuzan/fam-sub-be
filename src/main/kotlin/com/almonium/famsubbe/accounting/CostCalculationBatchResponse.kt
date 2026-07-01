package com.almonium.famsubbe.accounting

import java.util.*

data class CostCalculationBatchResponse(
    val id: UUID,
    val fromMonth: String, // Format: "YYYY-MM"
    val toMonth: String,   // Format: "YYYY-MM"
    val createdAt: String, // ISO timestamp
    val createdByAccountId: UUID,
    val createdByAccountName: String? = null
)
