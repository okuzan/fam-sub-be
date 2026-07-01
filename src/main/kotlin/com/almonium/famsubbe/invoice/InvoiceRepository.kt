package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.subscriber.Subscriber
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

    fun findFirstByOriginOrderByToMonthDesc(origin: InvoiceOrigin): Invoice?
    
    @Query("""
        select distinct i.subscriber.id
        from Invoice i
        where i.status in ('DRAFT', 'SENT')
    """)
    fun findSubscriberIdsWithUnpaidInvoices(): List<UUID>
    
    fun findBySubscriberAndStatusIn(subscriber: Subscriber, statuses: Collection<InvoiceStatus>): List<Invoice>

    fun findByInvoiceGenerationRunId(invoiceGenerationRunId: UUID): List<Invoice>

    fun findByStatusOrderByCreatedAtAsc(status: InvoiceStatus): List<Invoice>
}
