package com.almonium.famsubbe.subscriber

import java.math.BigDecimal
import java.util.*

data class SubscriberDebtPaymentResult(
    val subscriberId: UUID,
    val subscriberName: String,
    val attemptedCount: Int,
    val paidCount: Int,
    val totalPaidAmount: BigDecimal,
    val includeCredit: Boolean,
    val balanceBefore: BigDecimal,
    val balance: BigDecimal,
    val balanceAfter: BigDecimal,
    val creditWrittenOff: BigDecimal,
    val items: List<SubscriberDebtPaymentItemResult>
)

data class SubscriberDebtPaymentItemResult(
    val invoiceId: UUID,
    val statusBefore: String,
    val statusAfter: String,
    val invoiceAmount: BigDecimal,
    val paid: Boolean,
    val message: String
)
