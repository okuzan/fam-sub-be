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
        order by i.invoiceDate desc, i.createdAt desc
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

    /** Rows of (subscriber id, when their latest PAID invoice was marked paid). */
    @Query("""
        select i.subscriber.id, max(i.statusChangedAt)
        from Invoice i
        where i.status = 'PAID'
          and i.subscriber.id in :subscriberIds
        group by i.subscriber.id
    """)
    fun findLastPaidAtBySubscriberIds(subscriberIds: Collection<UUID>): List<Array<Any?>>

    @Query("""
        select i
        from Invoice i
        join fetch i.subscriber
        where i.status in :statuses
        order by i.createdAt asc
    """)
    fun findByStatusInWithSubscriber(statuses: Collection<InvoiceStatus>): List<Invoice>

    /** Invoices paid from credit when created whose receipt has not gone out yet. */
    @Query("""
        select i
        from Invoice i
        join fetch i.subscriber
        where i.status = 'PAID'
          and i.notes = :note
          and i.emailSent = false
        order by i.createdAt asc
    """)
    fun findUnsentReceipts(note: String): List<Invoice>

    fun findByInvoiceGenerationRunId(invoiceGenerationRunId: UUID): List<Invoice>

    fun findByStatusOrderByCreatedAtAsc(status: InvoiceStatus): List<Invoice>

    fun findByPaidByBankTransactionIdIn(bankTransactionIds: Collection<UUID>): List<Invoice>
}
