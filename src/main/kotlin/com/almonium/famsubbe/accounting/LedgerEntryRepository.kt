package com.almonium.famsubbe.accounting

import com.almonium.famsubbe.invoice.Invoice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.YearMonth
import java.util.*

@Repository
interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {
    fun findByRecordedMonth(recordedMonth: YearMonth): List<LedgerEntry>
    fun findBySubscriberId(subscriberId: UUID): List<LedgerEntry>

    fun existsByChargeId(chargeId: UUID): Boolean

    @Query("""
        select le
        from LedgerEntry le
        where le.invoice is null
          and le.recordedMonth >= :fromMonth
          and le.recordedMonth <= :toMonth
        order by le.subscriber.id asc, le.recordedMonth asc, le.id asc
    """)
    fun findUninvoicedByPeriod(
        fromMonth: YearMonth,
        toMonth: YearMonth
    ): List<LedgerEntry>

    @Query("""
        select le
        from LedgerEntry le
        where le.invoice is null
          and le.subscriber.id = :subscriberId
          and le.recordedMonth >= :fromMonth
          and le.recordedMonth <= :toMonth
        order by le.recordedMonth asc, le.id asc
    """)
    fun findUninvoicedBySubscriberAndPeriod(
        subscriberId: UUID,
        fromMonth: YearMonth,
        toMonth: YearMonth
    ): List<LedgerEntry>

    @Query("""
        select le
        from LedgerEntry le
        join fetch le.subscriptionService
        where le.invoice.id = :invoiceId
        order by le.recordedMonth asc, le.id asc
    """)
    fun findByInvoiceId(invoiceId: UUID): List<LedgerEntry>

    @Query("""
        select min(le.recordedMonth)
        from LedgerEntry le
        where le.invoice is null
    """)
    fun findOldestUninvoicedMonth(): YearMonth?

    @Query("""
        select max(le.recordedMonth)
        from LedgerEntry le
        where le.invoice is null
    """)
    fun findLatestUninvoicedMonth(): YearMonth?

    fun findByInvoice(invoice: Invoice): List<LedgerEntry>

    fun findByCalculationBatchId(calculationBatchId: UUID): List<LedgerEntry>

    fun countByCalculationBatchId(calculationBatchId: UUID): Long

    @Query("""
        select distinct le
        from LedgerEntry le
        join fetch le.charge charge
        join fetch le.subscriptionService service
        join fetch le.subscriber subscriber
        join fetch le.calculationBatch batch
        left join fetch batch.createdByAccount generatedBy
        left join fetch le.invoice invoice
        where (:id is null or le.id = :id)
          and (:chargeId is null or charge.id = :chargeId)
          and (:serviceId is null or service.id = :serviceId)
          and (:subscriberId is null or subscriber.id = :subscriberId)
          and (:recordedMonth is null or le.recordedMonth = :recordedMonth)
          and (:fromMonth is null or le.recordedMonth >= :fromMonth)
          and (:toMonth is null or le.recordedMonth <= :toMonth)
          and (:calculationBatchId is null or batch.id = :calculationBatchId)
          and (:generatedByAccountId is null or batch.createdByAccountId = :generatedByAccountId)
          and (:invoiceId is null or invoice.id = :invoiceId)
        order by batch.createdAt desc, le.recordedMonth desc, service.name asc, subscriber.name asc, le.id asc
    """)
    fun findVisibleEntries(
        id: UUID?,
        chargeId: UUID?,
        serviceId: UUID?,
        subscriberId: UUID?,
        recordedMonth: YearMonth?,
        fromMonth: YearMonth?,
        toMonth: YearMonth?,
        calculationBatchId: UUID?,
        generatedByAccountId: UUID?,
        invoiceId: UUID?
    ): List<LedgerEntry>

    @Query("""
        select count(le)
        from LedgerEntry le
        where le.invoice.invoiceGenerationRun.id = :invoiceGenerationRunId
    """)
    fun countByInvoiceGenerationRunId(invoiceGenerationRunId: UUID): Long
}
