package com.almonium.famsubbe.dto

import java.time.Instant
import java.util.*

data class InvoiceStatusHistoryResponse(
    val actionId: UUID,
    val changedAt: Instant,
    val changedByAccountId: UUID,
    val statusBefore: String?,
    val statusAfter: String,
    val actionType: String,
    val summary: String
)
