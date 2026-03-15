package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID

data class InvoiceGenerationResult(
    val invoicesCreated: Int,
    val ledgerEntriesAssigned: Int,
    val totalAmount: BigDecimal,
    val items: List<InvoiceGenerationItemResult>
)

data class InvoiceGenerationItemResult(
    val invoiceId: UUID,
    val subscriberId: UUID,
    val subscriberName: String,
    val fromMonth: YearMonth,
    val toMonth: YearMonth,
    val totalAmount: BigDecimal,
    val ledgerEntryCount: Int,
    val emailRequested: Boolean,
    val emailSent: Boolean,
    val message: String
)