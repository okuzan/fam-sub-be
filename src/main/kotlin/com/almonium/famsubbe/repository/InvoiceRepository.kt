package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.Invoice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    @Query("""
        select i
        from Invoice i
        where (:subscriberId is null or i.subscriber.id = :subscriberId)
        order by i.createdAt desc
    """)
    fun findAllFiltered(subscriberId: UUID?): List<Invoice>

    fun findFirstByOrderByToMonthDesc(): Invoice?
}