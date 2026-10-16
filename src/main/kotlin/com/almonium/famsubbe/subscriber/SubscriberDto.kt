package com.almonium.famsubbe.subscriber

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class SubscriberResponse(
    val id: UUID,
    val name: String,
    val surname: String?,
    val email: String,
    val balance: BigDecimal,
    val billingExempt: Boolean,
    val createdAt: Date,
    val updatedAt: Date,
    /** No current or upcoming membership, nothing owed or waiting to be invoiced, and no credit. */
    val archived: Boolean,
    /** Services they were ever a member of, for the archived view. */
    val formerServices: List<String>,
    /** The latest membership end month, or null while any membership is open-ended. */
    val lastMembershipEndMonth: String?,
    val lastInvoicePaidAt: Instant?
)

data class SubscriberCreateRequest(
    @field:NotBlank(message = "name is required")
    val name: String,
    val surname: String? = null,
    @field:NotBlank(message = "email is required")
    @field:Email(message = "email must be valid")
    val email: String,
    @field:Digits(integer = 17, fraction = 2, message = "balance must have at most 2 decimal places")
    @field:DecimalMin(value = "0.00", message = "balance must be a credit amount and cannot be negative")
    val balance: BigDecimal = BigDecimal.ZERO,
    val billingExempt: Boolean = false
)

data class SubscriberUpdateRequest(
    @field:NotBlank(message = "name is required")
    val name: String,
    val surname: String? = null,
    @field:NotBlank(message = "email is required")
    @field:Email(message = "email must be valid")
    val email: String,
    @field:Digits(integer = 17, fraction = 2, message = "balance must have at most 2 decimal places")
    @field:DecimalMin(value = "0.00", message = "balance must be a credit amount and cannot be negative")
    val balance: BigDecimal,
    val billingExempt: Boolean? = null
)
