package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.AdminActionResponse
import com.almonium.famsubbe.entity.CostCalculationBatch
import com.almonium.famsubbe.entity.InvoiceGenerationRun
import com.almonium.famsubbe.repository.CostCalculationBatchRepository
import com.almonium.famsubbe.repository.InvoiceGenerationRunRepository
import com.almonium.famsubbe.repository.InvoiceRepository
import com.almonium.famsubbe.repository.LedgerEntryRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class AdminActionService(
    private val costCalculationBatchRepository: CostCalculationBatchRepository,
    private val invoiceGenerationRunRepository: InvoiceGenerationRunRepository,
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) {
    fun getActions(limit: Int = DEFAULT_LIMIT): List<AdminActionResponse> {
        val resolvedLimit = limit.coerceIn(1, MAX_LIMIT)
        return (getCostRuns(resolvedLimit) + getInvoiceRuns(resolvedLimit))
            .sortedByDescending { it.createdAt }
            .take(resolvedLimit)
    }

    fun getCostRuns(limit: Int = DEFAULT_LIMIT): List<AdminActionResponse> {
        return costCalculationBatchRepository.findAll(createdAtDescending())
            .take(limit.coerceIn(1, MAX_LIMIT))
            .map { it.toAdminActionResponse() }
    }

    fun getInvoiceRuns(limit: Int = DEFAULT_LIMIT): List<AdminActionResponse> {
        return invoiceGenerationRunRepository.findAll(createdAtDescending())
            .take(limit.coerceIn(1, MAX_LIMIT))
            .map { it.toAdminActionResponse() }
    }

    private fun CostCalculationBatch.toAdminActionResponse(): AdminActionResponse {
        val batchId = requireNotNull(id)
        val from = requireNotNull(fromMonth)
        val to = requireNotNull(toMonth)
        val ledgerEntriesCreated = ledgerEntryRepository.countByCalculationBatchId(batchId)

        return AdminActionResponse(
            id = batchId,
            type = "COST_CALCULATION_RUN",
            createdAt = requireNotNull(createdAt),
            createdByAccountId = requireNotNull(createdByAccountId),
            fromMonth = from,
            toMonth = to,
            subscriberId = null,
            summary = "Calculated costs for $from to $to",
            metrics = mapOf(
                "ledgerEntriesCreated" to ledgerEntriesCreated
            )
        )
    }

    private fun InvoiceGenerationRun.toAdminActionResponse(): AdminActionResponse {
        val runId = requireNotNull(id)
        val from = requireNotNull(fromMonth)
        val to = requireNotNull(toMonth)
        val invoices = invoiceRepository.findByInvoiceGenerationRunId(runId)
        val totalAmount = invoices
            .map { it.totalAmount ?: BigDecimal.ZERO }
            .fold(BigDecimal.ZERO, BigDecimal::add)
        val ledgerEntriesAssigned = ledgerEntryRepository.countByInvoiceGenerationRunId(runId)

        return AdminActionResponse(
            id = runId,
            type = "INVOICE_GENERATION_RUN",
            createdAt = requireNotNull(createdAt),
            createdByAccountId = requireNotNull(createdByAccountId),
            fromMonth = from,
            toMonth = to,
            subscriberId = subscriberId,
            summary = "Generated ${invoices.size} invoices for $from to $to",
            metrics = mapOf(
                "invoicesCreated" to invoices.size,
                "ledgerEntriesAssigned" to ledgerEntriesAssigned,
                "totalAmount" to totalAmount,
                "sendEmail" to sendEmail
            )
        )
    }

    private fun createdAtDescending(): Sort =
        Sort.by(Sort.Direction.DESC, "createdAt")

    companion object {
        private const val DEFAULT_LIMIT = 50
        private const val MAX_LIMIT = 200
    }
}
