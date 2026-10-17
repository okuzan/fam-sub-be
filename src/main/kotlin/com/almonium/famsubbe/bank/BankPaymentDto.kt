package com.almonium.famsubbe.bank

import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class BankPaymentsOverview(
    val sync: BankSyncStatusResponse,
    val transactions: List<BankTransactionResponse>
)

data class BankSyncStatusResponse(
    val configured: Boolean,
    val syncEnabled: Boolean,
    val syncedTo: Instant?,
    val lastAttemptAt: Instant?,
    val lastSuccessAt: Instant?,
    val lastError: String?
)

data class BankTransactionResponse(
    val id: UUID,
    val occurredAt: Instant,
    val amount: BigDecimal,
    val senderName: String?,
    val description: String?,
    val comment: String?,
    val counterIban: String?,
    val status: String,
    val subscriberId: UUID?,
    val subscriberName: String?,
    val matchMethod: String?,
    val reviewNote: String?,
    val settledInvoiceCount: Int,
    val settledAmount: BigDecimal?,
    val resolvedAt: Instant?,
    /** Who the money most likely belongs to; only for UNMATCHED payments. */
    val suggestions: List<BankPaymentSuggestion>
)

data class BankPaymentSuggestion(
    val subscriberId: UUID,
    val subscriberName: String,
    val reason: String
)

data class BankPaymentAssignRequest(
    val subscriberId: UUID,
    /** Apply this sender's future payments to the same subscriber on their own. */
    val rememberSender: Boolean = true
)

data class BankPaymentIgnoreRequest(
    /** File this sender's future payments as IGNORED without asking. */
    val ignoreSender: Boolean = false
)

data class BankPaymentResolution(
    val transaction: BankTransactionResponse,
    val settledInvoiceIds: List<UUID>,
    val balanceAfter: BigDecimal?,
    val senderRemembered: Boolean
)

/** What undoing an applied payment changed. */
data class BankPaymentUndoResult(
    val transaction: BankTransactionResponse,
    /** Who the payment had been applied to. */
    val subscriberId: UUID,
    val subscriberName: String,
    val reopenedInvoiceIds: List<UUID>,
    val balanceAfter: BigDecimal
)

/** A payment as the subscriber sees it in their cabinet. */
data class SubscriberPaymentResponse(
    val id: UUID,
    val occurredAt: Instant,
    val amount: BigDecimal,
    val senderName: String?,
    /** The invoices it paid, as "2026-06" or "2026-04..2026-05"; the rest went to balance. */
    val settledPeriods: List<String>
)

data class BankSenderResponse(
    val id: UUID,
    val senderName: String?,
    val subscriberId: UUID?,
    val subscriberName: String?,
    val ignored: Boolean,
    val learnedAutomatically: Boolean,
    val createdAt: Instant
)

data class BankSyncResult(
    val fetchedCount: Int,
    val importedCount: Int,
    val appliedCount: Int,
    val needsReviewCount: Int,
    val ignoredCount: Int,
    val syncedTo: Instant
)

enum class BankImportOutcome {
    NOT_A_PAYMENT,
    DUPLICATE,
    APPLIED,
    NEEDS_REVIEW,
    IGNORED
}
