package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.LedgerEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.YearMonth
import java.util.UUID

@Repository
interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {
    fun findByRecordedMonth(recordedMonth: YearMonth): List<LedgerEntry>
    fun findBySubscriberId(subscriberId: UUID): List<LedgerEntry>
    fun existsByChargeId(chargeId: UUID): Boolean
}
