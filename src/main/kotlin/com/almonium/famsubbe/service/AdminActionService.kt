package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.AdminActionResponse
import com.almonium.famsubbe.entity.AdminAction
import com.almonium.famsubbe.entity.AdminActionTargetType
import com.almonium.famsubbe.entity.AdminActionType
import com.almonium.famsubbe.entity.CostCalculationBatch
import com.almonium.famsubbe.entity.InvoiceGenerationRun
import com.almonium.famsubbe.repository.AdminActionRepository
import com.almonium.famsubbe.repository.CostCalculationBatchRepository
import com.almonium.famsubbe.repository.InvoiceGenerationRunRepository
import com.almonium.famsubbe.repository.InvoiceRepository
import com.almonium.famsubbe.repository.LedgerEntryRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class AdminActionService(
    private val adminActionRepository: AdminActionRepository,
    private val costCalculationBatchRepository: CostCalculationBatchRepository,
    private val invoiceGenerationRunRepository: InvoiceGenerationRunRepository,
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val objectMapper: ObjectMapper
) {
    fun getActions(limit: Int = DEFAULT_LIMIT): List<AdminActionResponse> {
        return adminActionRepository.findAll(createdAtDescending())
            .take(limit.coerceIn(1, MAX_LIMIT))
            .map { it.toAdminActionResponse() }
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
            type = AdminActionType.COST_CALCULATION_RUN.name,
            createdAt = requireNotNull(createdAt),
            createdByAccountId = requireNotNull(createdByAccountId),
            targetType = AdminActionTargetType.COST_CALCULATION_RUN.name,
            targetId = batchId,
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
            type = AdminActionType.INVOICE_GENERATION_RUN.name,
            createdAt = requireNotNull(createdAt),
            createdByAccountId = requireNotNull(createdByAccountId),
            targetType = AdminActionTargetType.INVOICE_GENERATION_RUN.name,
            targetId = runId,
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

    private fun AdminAction.toAdminActionResponse(): AdminActionResponse {
        val metadata = metadataJson
            ?.takeIf { it.isNotBlank() }
            ?.let { objectMapper.readValue(it, mapTypeReference) }
            ?: emptyMap()

        return AdminActionResponse(
            id = requireNotNull(id),
            type = requireNotNull(actionType).name,
            createdAt = requireNotNull(createdAt),
            createdByAccountId = requireNotNull(createdByAccountId),
            targetType = requireNotNull(targetType).name,
            targetId = targetId,
            fromMonth = fromMonth,
            toMonth = toMonth,
            subscriberId = subscriberId,
            summary = requireNotNull(summary),
            metrics = metadata
        )
    }

    private fun createdAtDescending(): Sort =
        Sort.by(Sort.Direction.DESC, "createdAt")

    companion object {
        private const val DEFAULT_LIMIT = 50
        private const val MAX_LIMIT = 200
        private val mapTypeReference = object : TypeReference<Map<String, Any>>() {}
    }
}
