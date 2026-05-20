package com.almonium.famsubbe.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.YearMonth
import java.util.*

@Entity
@Table(name = "invoice_generation_run")
class InvoiceGenerationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "from_month", nullable = false, updatable = false)
    var fromMonth: YearMonth? = null

    @Column(name = "to_month", nullable = false, updatable = false)
    var toMonth: YearMonth? = null

    @Column(name = "subscriber_id", updatable = false)
    var subscriberId: UUID? = null

    @Column(name = "send_email", nullable = false, updatable = false)
    var sendEmail: Boolean = false

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    var createdByAccountId: UUID? = null
}