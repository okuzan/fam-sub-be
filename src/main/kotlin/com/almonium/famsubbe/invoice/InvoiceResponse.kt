package com.almonium.famsubbe.invoice

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.*

data class InvoiceResponse(
    val id: UUID,
    val subscriberId: UUID,
    val subscriberName: String,
    val fromMonth: YearMonth,
    val toMonth: YearMonth,
    val invoiceDate: LocalDate,
    val totalAmount: BigDecimal,
    val status: String,
    val createdAt: Instant,
    val statusChangedAt: Instant,
    val createdByAccountId: UUID,
    val invoiceGenerationRunId: UUID?,
    val sentAt: Instant?,
    val emailSent: Boolean,
    val notes: String?,
    val origin: String
)
