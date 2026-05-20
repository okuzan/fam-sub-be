package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.util.*

data class SubscriberResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val balance: BigDecimal,
    val autoPayInvoices: Boolean,
    val createdAt: Date,
    val updatedAt: Date
)

data class SubscriberCreateRequest(
    val name: String,
    val email: String,
    val balance: BigDecimal = BigDecimal.ZERO,
    val autoPayInvoices: Boolean = false
)

data class SubscriberUpdateRequest(
    val name: String,
    val email: String,
    val balance: BigDecimal,
    val autoPayInvoices: Boolean? = null
)
