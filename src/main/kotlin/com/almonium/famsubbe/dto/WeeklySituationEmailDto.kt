package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class WeeklySituationInvoiceDto(
    val id: UUID,
    val totalAmount: BigDecimal,
    val fromMonth: String,
    val toMonth: String,
    val createdAt: Instant,
    val status: String,
    val notes: String?,
    val origin: String,
    val ledgerEntries: List<WeeklySituationLedgerEntryDto>
) {
    val originLabel: String
        get() = origin
            .lowercase()
            .split("_")
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}

data class WeeklySituationLedgerEntryDto(
    val recordedMonth: String,
    val subscriptionServiceName: String,
    val notes: String?,
    val amount: BigDecimal
)

data class WeeklySituationEmailResult(
    val attemptedCount: Int,
    val sentCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val dryRun: Boolean,
    val items: List<WeeklySituationEmailItemResult>
)

data class WeeklySituationEmailItemResult(
    val subscriberId: UUID,
    val subscriberName: String,
    val email: String?,
    val totalOwed: BigDecimal,
    val sent: Boolean,
    val skipped: Boolean,
    val message: String
)
