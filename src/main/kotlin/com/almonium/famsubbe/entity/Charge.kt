package com.almonium.famsubbe.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.time.YearMonth
import java.util.*

@Entity
@Table(name = "charge")
class Charge {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_service_id", nullable = false)
    var subscriptionService: SubscriptionService? = null

    @Column(nullable = false)
    var amount: BigDecimal? = null

    @Column(nullable = false)
    var chargeMonth: YearMonth? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Date? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Date? = null
}
