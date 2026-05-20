package com.almonium.famsubbe.dto

import java.time.Instant
import java.time.YearMonth
import java.util.*

data class AdminActionResponse(
    val id: UUID,
    val type: String,
    val createdAt: Instant,
    val createdByAccountId: UUID,
    val fromMonth: YearMonth,
    val toMonth: YearMonth,
    val subscriberId: UUID?,
    val summary: String,
    val metrics: Map<String, Any>
)
