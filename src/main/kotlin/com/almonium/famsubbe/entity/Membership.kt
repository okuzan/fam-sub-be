package com.almonium.famsubbe.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.YearMonth
import java.util.*

@Entity
@Table(
    name = "membership",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_subscription_service_subscriber_month",
            columnNames = ["subscription_service_id", "subscriber_id", "membership_month"]
        )
    ]
)
class Membership {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_service_id", nullable = false)
    var subscriptionService: SubscriptionService? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    var subscriber: Subscriber? = null

    @Column(nullable = false, name = "membership_month")
    var membershipMonth: YearMonth? = null

    @Column(nullable = false)
    var shareWeight: Int = 1

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Date? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Date? = null
}
