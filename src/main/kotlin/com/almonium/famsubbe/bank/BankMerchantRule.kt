package com.almonium.famsubbe.bank

import com.almonium.famsubbe.subscription.SubscriptionService
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
import java.util.*

/**
 * What one subscription bill looks like in the statement: a card payment whose
 * merchant name contains [merchant] and whose original amount is exactly
 * [amount] in [currencyCode]. Several rules can point at one service (two
 * accounts, or a plan in two sizes).
 */
@Entity
@Table(name = "bank_merchant_rule")
class BankMerchantRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /** Lowercased; matched as a substring of the bank's merchant name. */
    @Column(nullable = false)
    var merchant: String? = null

    @Column(name = "currency_code", nullable = false)
    var currencyCode: Int? = null

    @Column(nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal? = null

    /** Null exactly when [ignored]. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_service_id")
    var subscriptionService: SubscriptionService? = null

    @Column(nullable = false)
    var ignored: Boolean = false

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null

    @Column(name = "created_by_account_id")
    var createdByAccountId: UUID? = null
}
