package com.almonium.famsubbe.admin

import com.almonium.famsubbe.invoice.InvoiceStatusHistoryResponse
import com.almonium.famsubbe.accounting.CostCalculationBatch
import com.almonium.famsubbe.accounting.CostCalculationBatchRepository
import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.invoice.InvoiceGenerationRun
import com.almonium.famsubbe.invoice.InvoiceGenerationRunRepository
import com.almonium.famsubbe.invoice.InvoiceRepository
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.*

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
    fun getActions(filter: AdminActionFilterRequest): List<AdminActionResponse> {
        return adminActionRepository.findAll(filter.toSpecification(), createdAtDescending())
            .take(filter.limit.coerceIn(1, MAX_LIMIT))
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

    fun getInvoiceStatusHistory(invoiceId: UUID): List<InvoiceStatusHistoryResponse> {
        val actionTypes = listOf(
            AdminActionType.INVOICE_MARKED_PAID,
            AdminActionType.INVOICE_STATUS_UPDATED,
            AdminActionType.INVOICE_PAID_FROM_BALANCE,
            AdminActionType.INVOICE_EMAIL_SENT,
            AdminActionType.INVOICE_VOIDED
        )

        return adminActionRepository
            .findByTargetTypeAndTargetIdAndActionTypeInOrderByCreatedAtAsc(
                AdminActionTargetType.INVOICE,
                invoiceId,
                actionTypes
            )
            .mapNotNull { it.toInvoiceStatusHistoryResponse() }
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
                "ledgerEntriesCreated" to ledgerEntriesCreated,
                "undoneAt" to undoneAt,
                "undoneByAccountId" to undoneByAccountId,
                "undoReason" to undoReason
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
                "sendEmail" to sendEmail,
                "undoneAt" to undoneAt,
                "undoneByAccountId" to undoneByAccountId,
                "undoReason" to undoReason
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

    private fun AdminAction.toInvoiceStatusHistoryResponse(): InvoiceStatusHistoryResponse? {
        val metadata = metadataJson
            ?.takeIf { it.isNotBlank() }
            ?.let { objectMapper.readValue(it, mapTypeReference) }
            ?: emptyMap()
        val statusAfter = metadata["statusAfter"]?.toString() ?: return null
        val statusBefore = metadata["statusBefore"]?.toString()
        if (statusBefore == statusAfter) {
            return null
        }

        return InvoiceStatusHistoryResponse(
            actionId = requireNotNull(id),
            changedAt = requireNotNull(createdAt),
            changedByAccountId = requireNotNull(createdByAccountId),
            statusBefore = statusBefore,
            statusAfter = statusAfter,
            actionType = requireNotNull(actionType).name,
            summary = requireNotNull(summary)
        )
    }

    private fun createdAtDescending(): Sort =
        Sort.by(Sort.Direction.DESC, "createdAt")

    private fun AdminActionFilterRequest.toSpecification(): Specification<AdminAction> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            actionType?.let {
                predicates += cb.equal(root.get<AdminActionType>("actionType"), it)
            }

            targetType?.let {
                predicates += cb.equal(root.get<AdminActionTargetType>("targetType"), it)
            }

            targetId?.let {
                predicates += cb.equal(root.get<java.util.UUID>("targetId"), it)
            }

            subscriberId?.let {
                predicates += cb.equal(root.get<java.util.UUID>("subscriberId"), it)
            }

            createdByAccountId?.let {
                predicates += cb.equal(root.get<java.util.UUID>("createdByAccountId"), it)
            }

            dateFrom?.let {
                predicates += cb.greaterThanOrEqualTo(root.get("createdAt"), it)
            }

            dateTo?.let {
                predicates += cb.lessThanOrEqualTo(root.get("createdAt"), it)
            }

            fromMonth?.let {
                predicates += cb.greaterThanOrEqualTo(root.get("fromMonth"), it)
            }

            toMonth?.let {
                predicates += cb.lessThanOrEqualTo(root.get("toMonth"), it)
            }

            cb.and(*predicates.toTypedArray())
        }

    companion object {
        private const val DEFAULT_LIMIT = 50
        private const val MAX_LIMIT = 200
        private val mapTypeReference = object : TypeReference<Map<String, Any?>>() {}
    }
}
