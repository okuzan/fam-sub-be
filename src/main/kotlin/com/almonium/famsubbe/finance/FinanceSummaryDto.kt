package com.almonium.famsubbe.finance

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class FinanceSummaryResponse(
    val generatedAt: Instant,
    val totalCabinetBalance: BigDecimal,
    val unpaidInvoiceCount: Int,
    val unpaidInvoiceAmount: BigDecimal,
    val balanceAppliedToDebt: BigDecimal,
    val netOutstandingDebt: BigDecimal,
    val debtorCount: Int,
    val averageDebt: BigDecimal,
    val unappliedCredit: BigDecimal,
    val oldestUnpaidInvoice: OldestUnpaidInvoiceResponse?,
    val debtors: List<DebtorFinanceResponse>
)

data class OldestUnpaidInvoiceResponse(
    val invoiceId: UUID,
    val subscriberId: UUID,
    val subscriberName: String,
    val amount: BigDecimal,
    val createdAt: Instant,
    val ageDays: Long
)

data class DebtorFinanceResponse(
    val subscriberId: UUID,
    val subscriberName: String,
    val unpaidInvoiceCount: Int,
    val unpaidInvoiceAmount: BigDecimal,
    val cabinetBalance: BigDecimal,
    val netDebt: BigDecimal,
    val debtSharePercent: BigDecimal
)
