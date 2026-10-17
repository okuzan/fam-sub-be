package com.almonium.famsubbe.invoice

import java.math.BigDecimal
import java.time.YearMonth
import java.util.*

data class InvoiceGenerationResult(
    val runId: UUID,
    val invoicesCreated: Int,
    val ledgerEntriesAssigned: Int,
    val totalAmount: BigDecimal,
    /** Invoices their balance covered in full, so they were created already paid. */
    val paidFromBalanceCount: Int,
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
    /** Created already paid from their balance; its email is a receipt, not a request. */
    val paidFromBalance: Boolean,
    val message: String
)