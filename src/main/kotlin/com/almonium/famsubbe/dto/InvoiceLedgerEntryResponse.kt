package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

data class InvoiceLedgerEntryResponse(
    val ledgerEntryId: UUID,
    val chargeId: UUID,
    val subscriptionServiceId: UUID,
    val subscriptionServiceName: String,
    val recordedMonth: YearMonth,
    val amount: BigDecimal,
    val participantCount: Int,
    val calculatedAt: Instant
)