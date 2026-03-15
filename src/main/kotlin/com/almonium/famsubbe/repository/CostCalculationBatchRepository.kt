package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.CostCalculationBatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.YearMonth
import java.util.*

@Repository
interface CostCalculationBatchRepository : JpaRepository<CostCalculationBatch, UUID> {
    
    @Query("SELECT b FROM CostCalculationBatch b WHERE b.fromMonth = :fromMonth ORDER BY b.createdAt DESC")
    fun findByFromMonth(fromMonth: YearMonth): List<CostCalculationBatch>
    
    @Query("SELECT b FROM CostCalculationBatch b WHERE b.createdByAccountId = :accountId ORDER BY b.createdAt DESC")
    fun findByCreatedByAccountId(accountId: UUID): List<CostCalculationBatch>
    
    @Query("SELECT b FROM CostCalculationBatch b ORDER BY b.createdAt DESC")
    fun findAllOrderByCreatedAtDesc(): List<CostCalculationBatch>
    
    @Query("SELECT b FROM CostCalculationBatch b ORDER BY b.toMonth DESC")
    fun findFirstByOrderByToMonthDesc(): CostCalculationBatch?
}
