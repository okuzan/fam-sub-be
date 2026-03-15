package com.almonium.famsubbe.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.time.YearMonth
import java.util.*

@Entity
@Table(name = "cost_calculation_batch")
class CostCalculationBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "from_month", nullable = false, updatable = false)
    var fromMonth: YearMonth? = null

    @Column(name = "to_month", nullable = false, updatable = false)
    var toMonth: YearMonth? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    var createdByAccountId: UUID? = null
}
