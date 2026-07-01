package com.almonium.famsubbe.subscriber

import com.almonium.famsubbe.invoice.InvoiceLedgerEntryResponse
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.*

data class SubscriberCabinetResponse(
    val subscriber: SubscriberCabinetProfileResponse,
    val balance: BigDecimal,
    val totalAmountOwed: BigDecimal,
    val activeSubscriptions: List<ActiveSubscriptionDto>,
    val unpaidInvoices: List<SubscriberInvoiceSummaryResponse>
)

data class SubscriberCabinetProfileResponse(
    val id: UUID,
    val name: String,
    val email: String
)

data class SubscriberInvoiceSummaryResponse(
    val id: UUID,
    val fromMonth: YearMonth,
    val toMonth: YearMonth,
    val invoiceDate: LocalDate,
    val totalAmount: BigDecimal,
    val status: String,
    val createdAt: Instant,
    val sentAt: Instant?,
    val notes: String?,
    val origin: String
)

data class SubscriberInvoiceDetailResponse(
    val invoice: SubscriberInvoiceSummaryResponse,
    val entries: List<InvoiceLedgerEntryResponse>
)
