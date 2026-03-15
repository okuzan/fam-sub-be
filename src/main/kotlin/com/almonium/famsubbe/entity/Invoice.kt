package com.almonium.famsubbe.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

@Entity
@Table(
    name = "invoice",
    indexes = [
        Index(name = "idx_invoice_subscriber", columnList = "subscriber_id"),
        Index(name = "idx_invoice_status", columnList = "status"),
        Index(name = "idx_invoice_from_month", columnList = "from_month"),
        Index(name = "idx_invoice_to_month", columnList = "to_month"),
        Index(name = "idx_invoice_created_at", columnList = "created_at")
    ]
)
class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false, updatable = false)
    var subscriber: Subscriber? = null

    @Column(name = "from_month", nullable = false, updatable = false)
    var fromMonth: YearMonth? = null

    @Column(name = "to_month", nullable = false, updatable = false)
    var toMonth: YearMonth? = null

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    var totalAmount: BigDecimal? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: InvoiceStatus = InvoiceStatus.DRAFT

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    var createdByAccountId: UUID? = null

    @Column(name = "sent_at")
    var sentAt: Instant? = null

    @Column(name = "email_sent", nullable = false)
    var emailSent: Boolean = false

    @Column(name = "notes")
    var notes: String? = null
}