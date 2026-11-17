package com.almonium.famsubbe.credit

import com.almonium.famsubbe.subscriber.Subscriber
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * An invoice the other way round: [subscriber] paid [amount] on the group's or
 * the admin's behalf on [occurredOn], for [reason]. It is credited to their
 * balance like an incoming payment, and settles what it can. Undoing it stamps
 * [undoneAt] instead of deleting the row, so the history stays.
 */
@Entity
@Table(name = "credit_note")
class CreditNote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false, updatable = false)
    var subscriber: Subscriber? = null

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    var amount: BigDecimal? = null

    @Column(name = "occurred_on", nullable = false, updatable = false)
    var occurredOn: LocalDate? = null

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    var reason: String? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    var createdByAccountId: UUID? = null

    /** Invoices it paid when it was recorded; see InvoiceService.applyIncomingPayment. */
    @Column(name = "settled_invoice_count", nullable = false)
    var settledInvoiceCount: Int = 0

    @Column(name = "settled_amount", nullable = false, precision = 19, scale = 2)
    var settledAmount: BigDecimal = BigDecimal.ZERO

    @Column(name = "email_sent_at")
    var emailSentAt: Instant? = null

    @Column(name = "undone_at")
    var undoneAt: Instant? = null

    @Column(name = "undone_by_account_id")
    var undoneByAccountId: UUID? = null
}
