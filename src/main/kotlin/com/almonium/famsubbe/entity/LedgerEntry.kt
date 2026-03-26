package com.almonium.famsubbe.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

@Entity
@Table(
    name = "ledger_entry",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_ledger_charge_subscriber",
            columnNames = ["charge_id", "subscriber_id"]
        )
    ],
    indexes = [
        Index(name = "idx_ledger_subscriber", columnList = "subscriber_id"),
        Index(name = "idx_ledger_charge", columnList = "charge_id"),
        Index(name = "idx_ledger_recorded_month", columnList = "recorded_month"),
        Index(name = "idx_ledger_calculation_batch", columnList = "calculation_batch_id"),
        Index(name = "idx_ledger_invoice", columnList = "invoice_id")
    ]
)
class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id", nullable = false, updatable = false)
    var charge: Charge? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false, updatable = false)
    var subscriber: Subscriber? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_service_id", nullable = false, updatable = false)
    var subscriptionService: SubscriptionService? = null

    @Column(name = "recorded_month", nullable = false, updatable = false)
    var recordedMonth: YearMonth? = null

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    var amount: BigDecimal? = null

    @Column(name = "participant_count", nullable = false, updatable = false)
    var participantCount: Int = 0

    @Column(name = "calculated_at", nullable = false, updatable = false)
    var calculatedAt: Instant? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_batch_id", nullable = false, updatable = false)
    var calculationBatch: CostCalculationBatch? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    var invoice: Invoice? = null

    @Column(name = "notes", updatable = false)
    var notes: String? = null
}
