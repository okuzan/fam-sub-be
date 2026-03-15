package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

data class InvoiceResponse(
    val id: UUID,
    val subscriberId: UUID,
    val subscriberName: String,
    val fromMonth: YearMonth,
    val toMonth: YearMonth,
    val totalAmount: BigDecimal,
    val status: String,
    val createdAt: Instant,
    val createdByAccountId: UUID,
    val sentAt: Instant?,
    val emailSent: Boolean,
    val notes: String?
)