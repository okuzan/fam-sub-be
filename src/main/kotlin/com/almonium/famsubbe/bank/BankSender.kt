package com.almonium.famsubbe.bank

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
import java.time.Instant
import java.util.*

/** A remembered sender: whose money their transfers are, or that they are not FamSub money at all. */
@Entity
@Table(name = "bank_sender")
class BankSender {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "sender_key", nullable = false, unique = true)
    var senderKey: String? = null

    @Column(name = "sender_name")
    var senderName: String? = null

    /** Null exactly when [ignored]. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id")
    var subscriber: Subscriber? = null

    @Column(nullable = false)
    var ignored: Boolean = false

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null

    /** Null when the system learned the sender on its own. */
    @Column(name = "created_by_account_id")
    var createdByAccountId: UUID? = null
}
