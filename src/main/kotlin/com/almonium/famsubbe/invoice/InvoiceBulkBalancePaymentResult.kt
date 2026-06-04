package com.almonium.famsubbe.invoice

import java.math.BigDecimal
import java.util.*

data class InvoiceBulkBalancePaymentResult(
    val attemptedCount: Int,
    val paidCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val totalPaidAmount: BigDecimal,
    val items: List<InvoiceBulkBalancePaymentItemResult>
)

data class InvoiceBulkBalancePaymentItemResult(
    val invoiceId: UUID,
    val subscriberId: UUID,
    val subscriberName: String,
    val statusBefore: String,
    val statusAfter: String,
    val invoiceAmount: BigDecimal,
    val balanceBefore: BigDecimal,
    val balanceAfter: BigDecimal,
    val paid: Boolean,
    val skipped: Boolean,
    val updated: Boolean,
    val message: String
)
