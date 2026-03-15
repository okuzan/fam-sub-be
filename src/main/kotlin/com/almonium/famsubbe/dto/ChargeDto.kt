package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.time.YearMonth
import java.util.*

data class ChargeCreateRequest(
    val subscriptionServiceId: UUID,
    val amount: BigDecimal,
    val chargeMonth: YearMonth
)

data class ChargeUpdateRequest(
    val amount: BigDecimal
)

data class ChargeResponse(
    val id: UUID,
    val subscriptionServiceId: UUID,
    val subscriptionServiceName: String,
    val amount: BigDecimal,
    val chargeMonth: YearMonth,
    val createdAt: Date,
    val updatedAt: Date
)
