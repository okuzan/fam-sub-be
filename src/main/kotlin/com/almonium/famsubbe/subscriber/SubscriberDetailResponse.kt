package com.almonium.famsubbe.subscriber

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

data class SubscriberDetailResponse(
    val id: UUID,
    val name: String,
    val email: String,
    /** The secret in their pay page link, <web-domain>/pay/<payToken>. */
    val payToken: String,
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
    val endMonth: String?,
    /** This month's share of the service price, split by weight across this month's members as a cost run would. */
    val monthlyShare: BigDecimal
)

data class UnpaidInvoiceDto(
    val id: UUID,
    val totalAmount: BigDecimal,
    val fromMonth: String,
    val toMonth: String,
    val invoiceDate: LocalDate,
    val createdAt: Instant,
    val status: String,
    val notes: String?,
    val origin: String
)
