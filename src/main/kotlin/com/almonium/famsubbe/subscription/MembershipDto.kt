package com.almonium.famsubbe.subscription

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.time.YearMonth
import java.util.*

data class MembershipCreateRequest(
    val subscriptionServiceId: UUID,
    val subscriberId: UUID,
    val startMonth: YearMonth,
    val endMonth: YearMonth?,
    /** Share relative to the other members; 1 for everyone is an even split. */
    @field:Min(1) @field:Max(MAX_SHARE_WEIGHT)
    val shareWeight: Int = 1
)

data class MembershipUpdateRequest(
    val startMonth: YearMonth,
    val endMonth: YearMonth?,
    /** Null keeps the current weight. */
    @field:Min(1) @field:Max(MAX_SHARE_WEIGHT)
    val shareWeight: Int? = null
)

const val MAX_SHARE_WEIGHT = 100L

data class MembershipEndRequest(
    val endMonth: YearMonth
)

data class MembershipResponse(
    val id: UUID,
    val subscriptionServiceId: UUID,
    val subscriptionServiceName: String,
    val subscriberId: UUID,
    val subscriberName: String,
    val startMonth: YearMonth,
    val endMonth: YearMonth?,
    val shareWeight: Int,
    val activeNow: Boolean,
    val createdAt: Instant
)
