package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.LedgerEntry
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
}
