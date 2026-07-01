package com.almonium.famsubbe.accounting

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface CostCalculationBatchRepository : JpaRepository<CostCalculationBatch, UUID> {
    fun findFirstByUndoneAtIsNullOrderByToMonthDesc(): CostCalculationBatch?

    @Query("""
        select distinct batch
        from CostCalculationBatch batch
        left join fetch batch.createdByAccount
        left join fetch batch.undoneByAccount
        order by batch.createdAt desc
    """)
    fun findAllVisibleOrderByCreatedAtDesc(): List<CostCalculationBatch>

    @Query("""
        select batch
        from CostCalculationBatch batch
        left join fetch batch.createdByAccount
        left join fetch batch.undoneByAccount
        where batch.id = :id
    """)
    fun findVisibleById(id: UUID): CostCalculationBatch?
}
