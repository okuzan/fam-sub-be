package com.almonium.famsubbe.subscription

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.util.*

data class SubscriptionServiceResponse(
    val id: UUID,
    val name: String,
    val price: BigDecimal,
    /** [price] is charged on its own at the start of every month. */
    val fixedMonthlyCharge: Boolean,
    val createdAt: Date,
    val updatedAt: Date
)

data class SubscriptionServiceCreateRequest(
    @field:NotBlank(message = "name is required")
    val name: String,
    @field:DecimalMin(value = "0.01", message = "price must be positive")
    @field:Digits(integer = 17, fraction = 2, message = "price must have at most 2 decimal places")
    val price: BigDecimal,
    val fixedMonthlyCharge: Boolean = false
)

data class SubscriptionServiceUpdateRequest(
    @field:NotBlank(message = "name is required")
    val name: String,
    @field:DecimalMin(value = "0.01", message = "price must be positive")
    @field:Digits(integer = 17, fraction = 2, message = "price must have at most 2 decimal places")
    val price: BigDecimal,
    /** Null keeps the current setting. */
    val fixedMonthlyCharge: Boolean? = null
)
