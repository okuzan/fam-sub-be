package com.almonium.famsubbe.bank

import com.almonium.famsubbe.accounting.Charge
import com.almonium.famsubbe.subscription.SubscriptionService
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

enum class BankDebitStatus {
    /** From a known merchant, but no rule says which bill it is, or its month is not open for it. */
    UNMATCHED,
    /** Part of a service's charge for [BankDebit.chargeMonth]. */
    CHARGED,
    /** Not a FamSub bill: a personal plan, a card check. */
    IGNORED
}

enum class BankChargeMatchMethod {
    /** Merchant, currency and amount matched a rule. */
    RULE,
    /** An admin charged it. */
    ADMIN
}

/** One card payment to a known subscription merchant, as read from the bank statement. */
@Entity
@Table(name = "bank_debit")
class BankDebit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    var provider: BankProvider = BankProvider.MONO

    @Column(name = "external_id", nullable = false, updatable = false)
    var externalId: String? = null

    @Column(name = "account_id", nullable = false, updatable = false)
    var accountId: String? = null

    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: Instant? = null

    /** What left the card, in UAH. */
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    var amount: BigDecimal? = null

    /** What the merchant charged, in [currencyCode]. */
    @Column(name = "original_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    var originalAmount: BigDecimal? = null

    /** ISO 4217 numeric: 840 USD, 978 EUR, 980 UAH. */
    @Column(name = "currency_code", nullable = false, updatable = false)
    var currencyCode: Int? = null

    @Column(columnDefinition = "TEXT", updatable = false)
    var description: String? = null

    @Column(updatable = false)
    var mcc: Int? = null

    /** The rule merchant it was recognised by. */
    @Column(nullable = false, updatable = false)
    var merchant: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BankDebitStatus = BankDebitStatus.UNMATCHED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_service_id")
    var subscriptionService: SubscriptionService? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id")
    var charge: Charge? = null

    @Column(name = "charge_month")
    var chargeMonth: YearMonth? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method")
    var matchMethod: BankChargeMatchMethod? = null

    /** Why it was not charged on its own, for the admin reviewing it. */
    @Column(name = "review_note", columnDefinition = "TEXT")
    var reviewNote: String? = null

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null

    /** Null when the system resolved it on its own. */
    @Column(name = "resolved_by_account_id")
    var resolvedByAccountId: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
