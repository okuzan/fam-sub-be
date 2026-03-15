package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

data class ChargeCreateRequest(
    val subscriptionServiceId: UUID,
    val amount: BigDecimal,
    val chargeMonth: YearMonth,
    val description: String?
)

data class ChargeUpdateRequest(
    val amount: BigDecimal,
    val description: String?
)

data class ChargeResponse(
    val id: UUID,
    val subscriptionServiceId: UUID,
    val subscriptionServiceName: String,
    val amount: BigDecimal,
    val chargeMonth: YearMonth,
    val description: String?,
    val createdAt: Instant
)
