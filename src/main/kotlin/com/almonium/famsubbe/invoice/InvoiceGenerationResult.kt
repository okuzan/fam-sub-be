package com.almonium.famsubbe.invoice

import java.math.BigDecimal
import java.time.YearMonth
import java.util.*

data class InvoiceGenerationResult(
    val runId: UUID,
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