package com.almonium.famsubbe.subscription

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.util.*

@Entity
@Table(name = "subscription_service")
class SubscriptionService {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(nullable = false)
    var name: String? = null

    /** The plan price in [currency], as the merchant states it. */
    @Column(nullable = false)
    var price: BigDecimal? = null

    /** ISO 4217 letter code, e.g. "USD". */
    @Column(nullable = false, length = 3)
    var currency: String = UAH

    /** Charge [price] on its own every month, for a bill fixed in UAH that is not matched from the card. */
    @Column(name = "fixed_monthly_charge", nullable = false)
    var fixedMonthlyCharge: Boolean = false

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Date? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Date? = null

    companion object {
        const val UAH = "UAH"
    }
}
