package com.almonium.famsubbe.dto

import java.time.YearMonth
import java.util.*

data class MembershipCreateRequest(
    val subscriptionServiceId: UUID,
    val subscriberId: UUID,
    val membershipMonth: YearMonth,
    val shareWeight: Int = 1
)

data class MembershipUpdateRequest(
    val shareWeight: Int
)

data class MembershipResponse(
    val id: UUID,
    val subscriptionServiceId: UUID,
    val subscriptionServiceName: String,
    val subscriberId: UUID,
    val subscriberName: String,
    val subscriberEmail: String,
    val membershipMonth: YearMonth,
    val shareWeight: Int,
    val createdAt: Date,
    val updatedAt: Date
)
