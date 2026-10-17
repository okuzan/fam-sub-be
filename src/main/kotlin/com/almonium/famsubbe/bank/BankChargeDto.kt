package com.almonium.famsubbe.bank

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

data class BankChargesOverview(
    val debits: List<BankDebitResponse>,
    val rules: List<BankMerchantRuleResponse>
)

data class BankDebitResponse(
    val id: UUID,
    val occurredAt: Instant,
    /** What left the card, in UAH. */
    val amount: BigDecimal,
    /** What the merchant charged, in [currency]. */
    val originalAmount: BigDecimal,
    /** ISO 4217 letter code, e.g. "USD". */
    val currency: String,
    val description: String?,
    val merchant: String,
    val status: String,
    val subscriptionServiceId: UUID?,
    val subscriptionServiceName: String?,
    val chargeId: UUID?,
    val chargeMonth: YearMonth?,
    /** The month this payment belongs to by its date; the default when charging it. */
    val suggestedChargeMonth: YearMonth,
    /** A service that already has a rule for this merchant; only for UNMATCHED debits. */
    val suggestedServiceId: UUID?,
    val matchMethod: String?,
    val reviewNote: String?,
    val resolvedAt: Instant?
)

data class BankMerchantRuleResponse(
    val id: UUID,
    val merchant: String,
    val currency: String,
    val amount: BigDecimal,
    val subscriptionServiceId: UUID?,
    val subscriptionServiceName: String?,
    val ignored: Boolean,
    val learnedAutomatically: Boolean,
    val createdAt: Instant
)

data class BankDebitChargeRequest(
    val subscriptionServiceId: UUID,
    /** Defaults to the month the payment belongs to by its date. */
    val chargeMonth: YearMonth? = null,
    /** Charge this merchant's next payment of the same amount to the same service on its own. */
    val rememberAmount: Boolean = true
)

data class BankDebitIgnoreRequest(
    /** File this merchant's next payment of the same amount as IGNORED without asking. */
    val rememberAmount: Boolean = false
)

data class BankMerchantRuleCreateRequest(
    @field:NotBlank(message = "merchant is required")
    val merchant: String,
    @field:Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a three-letter code such as USD")
    val currency: String,
    @field:DecimalMin(value = "0.01", message = "amount must be positive")
    @field:Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
    val amount: BigDecimal,
    /** Null makes it an ignore rule. */
    val subscriptionServiceId: UUID?
)

/** What undoing a charged debit changed. */
data class BankDebitUndoResult(
    val debit: BankDebitResponse,
    val chargeId: UUID,
    /** The charge was made of this payment alone and is gone. */
    val chargeDeleted: Boolean,
    val chargeAmountAfter: BigDecimal?
)

enum class BankDebitOutcome {
    NOT_A_SUBSCRIPTION,
    DUPLICATE,
    /** Still on hold at the bank; read again on a later sync. */
    PENDING,
    CHARGED,
    NEEDS_REVIEW,
    IGNORED
}
