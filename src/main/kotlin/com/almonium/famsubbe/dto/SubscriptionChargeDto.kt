package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.time.YearMonth
import java.util.*

data class SubscriptionChargeDto(
    val id: UUID?,
    val subscriptionServiceId: UUID,
    val amount: BigDecimal,
    val chargeDate: YearMonth
)

data class SubscriptionChargeCreateRequest(
    val subscriptionServiceId: UUID,
    val amount: BigDecimal,
    val chargeDate: YearMonth
)

data class SubscriptionChargeUpdateRequest(
    val amount: BigDecimal
)

data class SubscriptionChargeResponse(
    val id: UUID,
    val subscriptionServiceId: UUID,
    val subscriptionServiceName: String,
    val amount: BigDecimal,
    val chargeDate: YearMonth,
    val createdAt: Date,
    val updatedAt: Date
)
