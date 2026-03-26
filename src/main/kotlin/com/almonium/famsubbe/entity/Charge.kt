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
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

@Entity
@Table(
    name = "charge",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_charge_service_month",
            columnNames = ["subscription_service_id", "charge_month"]
        )
    ],
    indexes = [
        Index(name = "idx_charge_month", columnList = "charge_month")
    ]
)
class Charge {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_service_id", nullable = false)
    var subscriptionService: SubscriptionService? = null

    @Column(nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal? = null

    @Column(name = "charge_month", nullable = false)
    var chargeMonth: YearMonth? = null

    @Column(name = "description")
    var description: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
