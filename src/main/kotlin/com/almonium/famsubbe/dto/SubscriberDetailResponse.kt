package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class SubscriberDetailResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val balance: BigDecimal,
    val totalAmountOwed: BigDecimal,
    val activeSubscriptions: List<ActiveSubscriptionDto>,
    val unpaidInvoices: List<UnpaidInvoiceDto>
)

data class ActiveSubscriptionDto(
    val id: UUID,
    val serviceName: String,
    val servicePrice: BigDecimal,
    val startMonth: String,
    val endMonth: String?
)

data class UnpaidInvoiceDto(
    val id: UUID,
    val totalAmount: BigDecimal,
    val fromMonth: String,
    val toMonth: String,
    val createdAt: Instant,
    val status: String,
    val notes: String?
)
