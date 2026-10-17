package com.almonium.famsubbe.invoice

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
    /** This subscriber's weight out of [totalWeight]; equal to 1 of participantCount for an even split. */
    val shareWeight: Int,
    val totalWeight: Int,
    val calculatedAt: Instant
)