package com.almonium.famsubbe.admin

import java.time.Instant
import java.time.YearMonth
import java.util.*

data class AdminActionResponse(
    val id: UUID,
    val type: String,
    val createdAt: Instant,
    val createdByAccountId: UUID,
    val targetType: String,
    val targetId: UUID?,
    val fromMonth: YearMonth?,
    val toMonth: YearMonth?,
    val subscriberId: UUID?,
    val summary: String,
    val metrics: Map<String, Any?>
)
