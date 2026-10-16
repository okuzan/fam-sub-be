package com.almonium.famsubbe.subscriber

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.util.*

@Entity
@Table(name = "subscriber")
class Subscriber {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(nullable = false)
    var name: String? = null

    @Column(nullable = false, unique = true)
    var email: String? = null

    @Column(nullable = false)
    var balance: BigDecimal? = null

    /**
     * Family and dependants who take part in cost splits but are never billed.
     * Their invoices are created already settled; balance is untouched.
     */
    @Column(name = "billing_exempt", nullable = false)
    var billingExempt: Boolean = false

    /** The secret in their public pay page link; see PublicPayController. */
    @Column(name = "pay_token", nullable = false, unique = true, updatable = false)
    var payToken: String? = null

    /** Put in the payment purpose so their bank transfers are recognised; see PaymentCode. */
    @Column(name = "payment_code", nullable = false, unique = true, updatable = false)
    var paymentCode: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Date? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Date? = null

    @PrePersist
    fun assignPaymentIdentifiers() {
        if (payToken == null) payToken = PaymentCode.newPayToken()
        if (paymentCode == null) paymentCode = PaymentCode.newCode()
    }
}
