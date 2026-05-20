package com.almonium.famsubbe.dto

import com.almonium.famsubbe.entity.AdminActionTargetType
import com.almonium.famsubbe.entity.AdminActionType
import java.time.Instant
import java.time.YearMonth
import java.util.*

data class AdminActionFilterRequest(
    val actionType: AdminActionType? = null,
    val targetType: AdminActionTargetType? = null,
    val targetId: UUID? = null,
    val subscriberId: UUID? = null,
    val createdByAccountId: UUID? = null,
    val dateFrom: Instant? = null,
    val dateTo: Instant? = null,
    val fromMonth: YearMonth? = null,
    val toMonth: YearMonth? = null,
    val limit: Int = 50
)
