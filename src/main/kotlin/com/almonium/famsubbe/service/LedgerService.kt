package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.LedgerEntryResponse
import com.almonium.famsubbe.entity.LedgerEntry
import com.almonium.famsubbe.repository.LedgerEntryRepository
import org.springframework.stereotype.Service
import java.time.YearMonth
import java.util.*

@Service
class LedgerService(
    private val ledgerEntryRepository: LedgerEntryRepository
) {
    fun getByMonth(month: YearMonth): List<LedgerEntryResponse> =
        ledgerEntryRepository.findByRecordedMonth(month).map { it.toResponse() }

    fun getBySubscriber(subscriberId: UUID): List<LedgerEntryResponse> =
        ledgerEntryRepository.findBySubscriberId(subscriberId).map { it.toResponse() }

    private fun LedgerEntry.toResponse(): LedgerEntryResponse = LedgerEntryResponse(
        id = id!!,
        chargeId = charge!!.id!!,
        subscriptionServiceId = subscriptionService!!.id!!,
        subscriptionServiceName = subscriptionService!!.name!!,
        subscriberId = subscriber!!.id!!,
        subscriberName = subscriber!!.name!!,
        recordedMonth = recordedMonth!!,
        amount = amount!!,
        participantCount = participantCount,
        calculatedAt = calculatedAt!!,
        calculationBatchId = calculationBatch!!.id!!
    )
}
