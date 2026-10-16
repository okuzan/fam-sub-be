package com.almonium.famsubbe.bank

import com.almonium.famsubbe.subscriber.Subscriber
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
import java.util.*

enum class BankProvider { MONO }

enum class BankTransactionStatus {
    /** Waiting for an admin to say whose money it is, or to ignore it. */
    UNMATCHED,
    /** Credited to a subscriber's balance and used to settle their invoices. */
    APPLIED,
    /** Not FamSub money. */
    IGNORED
}

enum class BankMatchMethod {
    /** The purpose carried the subscriber's payment code. */
    PAYMENT_CODE,
    /** The sender's name holds the subscriber's name and surname. */
    NAME,
    /** The sender was already known. */
    SENDER_RULE,
    /** An admin assigned it. */
    ADMIN
}

/** One incoming transfer from another person, as read from the bank statement. */
@Entity
@Table(name = "bank_transaction")
class BankTransaction {
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

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    var amount: BigDecimal? = null

    @Column(columnDefinition = "TEXT", updatable = false)
    var description: String? = null

    @Column(columnDefinition = "TEXT", updatable = false)
    var comment: String? = null

    @Column(name = "counter_name", updatable = false)
    var counterName: String? = null

    @Column(name = "counter_iban", updatable = false)
    var counterIban: String? = null

    @Column(name = "sender_key", updatable = false)
    var senderKey: String? = null

    @Column(name = "sender_name", updatable = false)
    var senderName: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BankTransactionStatus = BankTransactionStatus.UNMATCHED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id")
    var subscriber: Subscriber? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method")
    var matchMethod: BankMatchMethod? = null

    /** Why it was not applied on its own, for the admin reviewing it. */
    @Column(name = "review_note", columnDefinition = "TEXT")
    var reviewNote: String? = null

    @Column(name = "settled_invoice_count", nullable = false)
    var settledInvoiceCount: Int = 0

    @Column(name = "settled_amount", precision = 19, scale = 2)
    var settledAmount: BigDecimal? = null

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null

    /** Null when the system resolved it on its own. */
    @Column(name = "resolved_by_account_id")
    var resolvedByAccountId: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
