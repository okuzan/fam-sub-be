package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.RunRecoveryPreviewResponse
import com.almonium.famsubbe.dto.RunUndoResponse
import com.almonium.famsubbe.entity.AdminActionTargetType
import com.almonium.famsubbe.entity.AdminActionType
import com.almonium.famsubbe.entity.InvoiceOrigin
import com.almonium.famsubbe.entity.InvoiceStatus
import com.almonium.famsubbe.repository.CostCalculationBatchRepository
import com.almonium.famsubbe.repository.InvoiceGenerationRunRepository
import com.almonium.famsubbe.repository.InvoiceRepository
import com.almonium.famsubbe.repository.LedgerEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
@Transactional
class RecoveryService(
    private val costCalculationBatchRepository: CostCalculationBatchRepository,
    private val invoiceGenerationRunRepository: InvoiceGenerationRunRepository,
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val adminAuditLogService: AdminAuditLogService
) {
    @Transactional(readOnly = true)
    fun previewCostRunUndo(runId: UUID): RunRecoveryPreviewResponse {
        val run = costCalculationBatchRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Cost calculation run not found: $runId") }
        val ledgerEntries = ledgerEntryRepository.findByCalculationBatchId(runId)
        val linkedInvoices = ledgerEntries.mapNotNull { it.invoice }.distinctBy { it.id }
        val blockers = mutableListOf<String>()

        if (run.undoneAt != null) {
            blockers += "Run is already undone"
        }
        if (linkedInvoices.isNotEmpty()) {
            blockers += "Run has ${linkedInvoices.size} invoiced ledger groups. Undo invoice runs first."
        }

        return RunRecoveryPreviewResponse(
            runId = runId,
            type = AdminActionType.COST_CALCULATION_RUN.name,
            allowed = blockers.isEmpty(),
            alreadyUndone = run.undoneAt != null,
            blockers = blockers,
            effects = mapOf(
                "ledgerEntriesDeleted" to ledgerEntries.size
            ),
            summary = "Undo cost calculation run ${run.fromMonth} to ${run.toMonth}"
        )
    }

    fun undoCostRun(runId: UUID, performedByAccountId: UUID, reason: String?): RunUndoResponse {
        val preview = previewCostRunUndo(runId)
        check(preview.allowed) { preview.blockers.joinToString("; ") }

        val run = costCalculationBatchRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Cost calculation run not found: $runId") }
        val ledgerEntries = ledgerEntryRepository.findByCalculationBatchId(runId)
        if (ledgerEntries.isNotEmpty()) {
            ledgerEntryRepository.deleteAll(ledgerEntries)
        }

        val undoneAt = Instant.now()
        run.undoneAt = undoneAt
        run.undoneByAccountId = performedByAccountId
        run.undoReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        costCalculationBatchRepository.save(run)

        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.COST_CALCULATION_RUN_UNDONE,
            targetType = AdminActionTargetType.COST_CALCULATION_RUN,
            targetId = runId,
            fromMonth = run.fromMonth,
            toMonth = run.toMonth,
            summary = "Undid cost calculation run $runId",
            metadata = mapOf(
                "ledgerEntriesDeleted" to ledgerEntries.size,
                "reason" to run.undoReason
            )
        )

        return RunUndoResponse(
            runId = runId,
            type = AdminActionType.COST_CALCULATION_RUN.name,
            undoneAt = undoneAt,
            summary = "Cost calculation run undone",
            effects = mapOf("ledgerEntriesDeleted" to ledgerEntries.size)
        )
    }

    @Transactional(readOnly = true)
    fun previewInvoiceRunUndo(runId: UUID): RunRecoveryPreviewResponse {
        val run = invoiceGenerationRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Invoice generation run not found: $runId") }
        val invoices = invoiceRepository.findByInvoiceGenerationRunId(runId)
        val blockers = mutableListOf<String>()

        if (run.undoneAt != null) {
            blockers += "Run is already undone"
        }

        val invalidInvoices = invoices.filter {
            it.origin != InvoiceOrigin.SUBSCRIPTION_LEDGER || it.status != InvoiceStatus.DRAFT
        }
        invalidInvoices.forEach { invoice ->
            blockers += "Invoice ${invoice.id} has status ${invoice.status} and origin ${invoice.origin}"
        }

        val ledgerEntriesReleased = invoices.sumOf { ledgerEntryRepository.findByInvoice(it).size }

        return RunRecoveryPreviewResponse(
            runId = runId,
            type = AdminActionType.INVOICE_GENERATION_RUN.name,
            allowed = blockers.isEmpty(),
            alreadyUndone = run.undoneAt != null,
            blockers = blockers,
            effects = mapOf(
                "invoicesDeleted" to invoices.size,
                "ledgerEntriesReleased" to ledgerEntriesReleased
            ),
            summary = "Undo invoice generation run ${run.fromMonth} to ${run.toMonth}"
        )
    }

    fun undoInvoiceRun(runId: UUID, performedByAccountId: UUID, reason: String?): RunUndoResponse {
        val preview = previewInvoiceRunUndo(runId)
        check(preview.allowed) { preview.blockers.joinToString("; ") }

        val run = invoiceGenerationRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Invoice generation run not found: $runId") }
        val invoices = invoiceRepository.findByInvoiceGenerationRunId(runId)
        val ledgerEntries = invoices.flatMap { ledgerEntryRepository.findByInvoice(it) }

        ledgerEntries.forEach { it.invoice = null }
        if (ledgerEntries.isNotEmpty()) {
            ledgerEntryRepository.saveAll(ledgerEntries)
        }
        if (invoices.isNotEmpty()) {
            invoiceRepository.deleteAll(invoices)
        }

        val undoneAt = Instant.now()
        run.undoneAt = undoneAt
        run.undoneByAccountId = performedByAccountId
        run.undoReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        invoiceGenerationRunRepository.save(run)

        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.INVOICE_GENERATION_RUN_UNDONE,
            targetType = AdminActionTargetType.INVOICE_GENERATION_RUN,
            targetId = runId,
            subscriberId = run.subscriberId,
            fromMonth = run.fromMonth,
            toMonth = run.toMonth,
            summary = "Undid invoice generation run $runId",
            metadata = mapOf(
                "invoicesDeleted" to invoices.size,
                "ledgerEntriesReleased" to ledgerEntries.size,
                "reason" to run.undoReason
            )
        )

        return RunUndoResponse(
            runId = runId,
            type = AdminActionType.INVOICE_GENERATION_RUN.name,
            undoneAt = undoneAt,
            summary = "Invoice generation run undone",
            effects = mapOf(
                "invoicesDeleted" to invoices.size,
                "ledgerEntriesReleased" to ledgerEntries.size
            )
        )
    }
}
