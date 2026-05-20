package com.almonium.famsubbe.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

data class LedgerEntryResponse(
    val id: UUID,
    val chargeId: UUID,
    val chargeMonth: YearMonth,
    val chargeDescription: String?,
    val subscriptionServiceId: UUID,
    val subscriptionServiceName: String,
    val subscriberId: UUID,
    val subscriberName: String,
    val recordedMonth: YearMonth,
    val amount: BigDecimal,
    val participantCount: Int,
    val calculatedAt: Instant,
    val calculationBatchId: UUID,
    val calculationBatchFromMonth: YearMonth,
    val calculationBatchToMonth: YearMonth,
    val generatedByAccountId: UUID,
    val generatedByAccountName: String?,
    val invoiceId: UUID?,
    val notes: String?
)

data class LedgerEntryFilterRequest(
    val id: UUID? = null,
    val chargeId: UUID? = null,
    val serviceId: UUID? = null,
    val subscriptionServiceId: UUID? = null,
    val subscriberId: UUID? = null,
    val recordedMonth: YearMonth? = null,
    val fromMonth: YearMonth? = null,
    val toMonth: YearMonth? = null,
    val calculationBatchId: UUID? = null,
    val generatedByAccountId: UUID? = null,
    val invoiceId: UUID? = null
)

data class LedgerCalculationBatchResponse(
    val id: UUID,
    val fromMonth: YearMonth,
    val toMonth: YearMonth,
    val createdAt: Instant,
    val generatedByAccountId: UUID,
    val generatedByAccountName: String?,
    val ledgerEntryCount: Long,
    val undoneAt: Instant?,
    val undoneByAccountId: UUID?,
    val undoneByAccountName: String?,
    val undoReason: String?
)
