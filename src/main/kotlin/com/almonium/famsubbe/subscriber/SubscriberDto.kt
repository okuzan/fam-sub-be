package com.almonium.famsubbe.subscriber

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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

/**
 * There is no balance here or in the update: credit only moves through
 * something that records why, a credit note, a bank payment or an invoice.
 * A balance a client still sends is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SubscriberCreateRequest(
    @field:NotBlank(message = "name is required")
    val name: String,
    val surname: String? = null,
    @field:NotBlank(message = "email is required")
    @field:Email(message = "email must be valid")
    val email: String,
    val billingExempt: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SubscriberUpdateRequest(
    @field:NotBlank(message = "name is required")
    val name: String,
    val surname: String? = null,
    @field:NotBlank(message = "email is required")
    @field:Email(message = "email must be valid")
    val email: String,
    val billingExempt: Boolean? = null
)
