package com.almonium.famsubbe.dto

import java.time.Instant
import java.time.YearMonth
import java.util.*

data class MembershipCreateRequest(
    val subscriptionServiceId: UUID,
    val subscriberId: UUID,
    val startMonth: YearMonth,
    val endMonth: YearMonth?
)

data class MembershipUpdateRequest(
    val startMonth: YearMonth,
    val endMonth: YearMonth?
)

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
    val activeNow: Boolean,
    val createdAt: Instant
)
