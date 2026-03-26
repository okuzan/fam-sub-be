package com.almonium.famsubbe.entity

import com.almonium.famsubbe.dto.CostCalculationBatchResponse
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
@Table(name = "cost_calculation_batch")
class CostCalculationBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "from_month", nullable = false, updatable = false)
    var fromMonth: YearMonth? = null

    @Column(name = "to_month", nullable = false, updatable = false)
    var toMonth: YearMonth? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    var createdByAccountId: UUID? = null
}

fun CostCalculationBatch.toResponse(): CostCalculationBatchResponse {
    return CostCalculationBatchResponse(
        id = this.id!!,
        fromMonth = this.fromMonth.toString(),
        toMonth = this.toMonth.toString(),
        createdAt = this.createdAt.toString(),
        createdByAccountId = this.createdByAccountId!!
    )
}
