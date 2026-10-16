package com.almonium.famsubbe.accounting

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

data class ChargeCreateRequest(
    val subscriptionServiceId: UUID,
    @field:DecimalMin(value = "0.01", message = "amount must be positive")
    @field:Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
    val amount: BigDecimal,
    val chargeMonth: YearMonth,
    val description: String?
)

data class ChargeUpdateRequest(
    @field:DecimalMin(value = "0.01", message = "amount must be positive")
    @field:Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
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

data class ChargePageResponse(
    val content: List<ChargeResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
