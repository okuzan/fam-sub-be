package com.almonium.famsubbe.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

@Entity
@Table(
    name = "membership",
    indexes = [
        Index(name = "idx_membership_subscriber", columnList = "subscriber_id"),
        Index(name = "idx_membership_service", columnList = "subscription_service_id"),
        Index(name = "idx_membership_start_month", columnList = "start_month"),
        Index(name = "idx_membership_end_month", columnList = "end_month")
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

    @Column(name = "start_month", nullable = false)
    var startMonth: YearMonth? = null

    @Column(name = "end_month")
    var endMonth: YearMonth? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
