package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.LedgerCalculationBatchResponse
import com.almonium.famsubbe.dto.LedgerEntryFilterRequest
import com.almonium.famsubbe.dto.LedgerEntryResponse
import com.almonium.famsubbe.entity.CostCalculationBatch
import com.almonium.famsubbe.entity.LedgerEntry
import com.almonium.famsubbe.repository.CostCalculationBatchRepository
import com.almonium.famsubbe.repository.LedgerEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth
import java.util.*

@Service
class LedgerService(
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val costCalculationBatchRepository: CostCalculationBatchRepository
) {
    @Transactional(readOnly = true)
    fun getByMonth(month: YearMonth): List<LedgerEntryResponse> =
        filter(LedgerEntryFilterRequest(recordedMonth = month))

    @Transactional(readOnly = true)
    fun getBySubscriber(subscriberId: UUID): List<LedgerEntryResponse> =
        filter(LedgerEntryFilterRequest(subscriberId = subscriberId))

    @Transactional(readOnly = true)
    fun getById(id: UUID): LedgerEntryResponse =
        filter(LedgerEntryFilterRequest(id = id)).firstOrNull()
            ?: throw IllegalArgumentException("Ledger entry not found: $id")

    @Transactional(readOnly = true)
    fun getByCalculationBatch(calculationBatchId: UUID): List<LedgerEntryResponse> =
        filter(LedgerEntryFilterRequest(calculationBatchId = calculationBatchId))

    @Transactional(readOnly = true)
    fun filter(filter: LedgerEntryFilterRequest): List<LedgerEntryResponse> {
        val serviceId = resolveServiceId(filter.serviceId, filter.subscriptionServiceId)
        return ledgerEntryRepository.findVisibleEntries(
            id = filter.id,
            chargeId = filter.chargeId,
            serviceId = serviceId,
            subscriberId = filter.subscriberId,
            recordedMonth = filter.recordedMonth,
            fromMonth = filter.fromMonth,
            toMonth = filter.toMonth,
            calculationBatchId = filter.calculationBatchId,
            generatedByAccountId = filter.generatedByAccountId,
            invoiceId = filter.invoiceId
        ).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getRecentCalculationBatches(limit: Int = 10): List<LedgerCalculationBatchResponse> =
        costCalculationBatchRepository.findAllVisibleOrderByCreatedAtDesc()
            .take(limit.coerceIn(1, 100))
            .map { it.toLedgerCalculationBatchResponse() }

    @Transactional(readOnly = true)
    fun getLatestCalculationBatch(): LedgerCalculationBatchResponse? =
        costCalculationBatchRepository.findAllVisibleOrderByCreatedAtDesc()
            .firstOrNull()
            ?.toLedgerCalculationBatchResponse()

    @Transactional(readOnly = true)
    fun getCalculationBatch(calculationBatchId: UUID): LedgerCalculationBatchResponse =
        costCalculationBatchRepository.findVisibleById(calculationBatchId)
            ?.toLedgerCalculationBatchResponse()
            ?: throw IllegalArgumentException("Cost calculation batch not found: $calculationBatchId")

    private fun resolveServiceId(serviceId: UUID?, subscriptionServiceId: UUID?): UUID? {
        require(serviceId == null || subscriptionServiceId == null || serviceId == subscriptionServiceId) {
            "serviceId and subscriptionServiceId must match when both are provided"
        }
        return serviceId ?: subscriptionServiceId
    }

    private fun LedgerEntry.toResponse(): LedgerEntryResponse = LedgerEntryResponse(
        id = id!!,
        chargeId = charge!!.id!!,
        chargeMonth = charge!!.chargeMonth!!,
        chargeDescription = charge!!.description,
        subscriptionServiceId = subscriptionService!!.id!!,
        subscriptionServiceName = subscriptionService!!.name!!,
        subscriberId = subscriber!!.id!!,
        subscriberName = subscriber!!.name!!,
        recordedMonth = recordedMonth!!,
        amount = amount!!,
        participantCount = participantCount,
        calculatedAt = calculatedAt!!,
        calculationBatchId = calculationBatch!!.id!!,
        calculationBatchFromMonth = calculationBatch!!.fromMonth!!,
        calculationBatchToMonth = calculationBatch!!.toMonth!!,
        generatedByAccountId = calculationBatch!!.createdByAccountId!!,
        generatedByAccountName = calculationBatch!!.createdByAccount?.email,
        invoiceId = invoice?.id,
        notes = notes
    )

    private fun CostCalculationBatch.toLedgerCalculationBatchResponse(): LedgerCalculationBatchResponse {
        val batchId = requireNotNull(id)
        return LedgerCalculationBatchResponse(
            id = batchId,
            fromMonth = fromMonth!!,
            toMonth = toMonth!!,
            createdAt = createdAt!!,
            generatedByAccountId = createdByAccountId!!,
            generatedByAccountName = createdByAccount?.email,
            ledgerEntryCount = ledgerEntryRepository.countByCalculationBatchId(batchId),
            undoneAt = undoneAt,
            undoneByAccountId = undoneByAccountId,
            undoneByAccountName = undoneByAccount?.email,
            undoReason = undoReason
        )
    }
}
