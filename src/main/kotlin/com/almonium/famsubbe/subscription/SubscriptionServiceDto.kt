package com.almonium.famsubbe.subscription

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal
import java.util.*

data class SubscriptionServiceResponse(
    val id: UUID,
    val name: String,
    /** In [currency], as the merchant states it. */
    val price: BigDecimal,
    val currency: String,
    /** [price] is charged on its own at the start of every month; only for UAH. */
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
    @field:Pattern(regexp = "[A-Z]{3}", message = "currency must be a three-letter code such as USD")
    val currency: String = SubscriptionService.UAH,
    val fixedMonthlyCharge: Boolean = false
)

data class SubscriptionServiceUpdateRequest(
    @field:NotBlank(message = "name is required")
    val name: String,
    @field:DecimalMin(value = "0.01", message = "price must be positive")
    @field:Digits(integer = 17, fraction = 2, message = "price must have at most 2 decimal places")
    val price: BigDecimal,
    /** Null keeps the current currency. */
    @field:Pattern(regexp = "[A-Z]{3}", message = "currency must be a three-letter code such as USD")
    val currency: String? = null,
    /** Null keeps the current setting. */
    val fixedMonthlyCharge: Boolean? = null
)
