package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.CostCalculationBatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface CostCalculationBatchRepository : JpaRepository<CostCalculationBatch, UUID> {
    fun findFirstByOrderByToMonthDesc(): CostCalculationBatch?
}
