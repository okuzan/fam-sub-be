package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.util.*

data class SubscriptionServiceResponse(
    val id: UUID,
    val name: String,
    val price: BigDecimal,
    val createdAt: Date,
    val updatedAt: Date
)

data class SubscriptionServiceCreateRequest(
    val name: String,
    val price: BigDecimal
)

data class SubscriptionServiceUpdateRequest(
    val name: String,
    val price: BigDecimal
)
