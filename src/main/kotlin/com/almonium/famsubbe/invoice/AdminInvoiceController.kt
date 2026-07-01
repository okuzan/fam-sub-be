package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.admin.AdminActionService
import com.almonium.famsubbe.admin.AdminActionTargetType
import com.almonium.famsubbe.admin.AdminActionType
import com.almonium.famsubbe.admin.AdminAuditLogService
import com.almonium.famsubbe.account.AccountService
import com.almonium.famsubbe.util.AuthenticationUtil
import jakarta.validation.Valid
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/admin/invoices")
class AdminInvoiceController(
    private val invoiceService: InvoiceService,
    private val accountService: AccountService,
    private val adminActionService: AdminActionService,
    private val adminAuditLogService: AdminAuditLogService
) {

    @PostMapping("/generate")
    fun generateInvoices(
        @RequestBody request: InvoiceGenerationRequest,
        authentication: Authentication
    ): ResponseEntity<InvoiceGenerationResult> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val result = invoiceService.generateInvoices(request, performedByAccountId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.INVOICE_GENERATION_RUN,
            targetType = AdminActionTargetType.INVOICE_GENERATION_RUN,
            targetId = result.runId,
            subscriberId = request.subscriberId,
            fromMonth = request.fromMonth,
            toMonth = request.toMonth,
            summary = "Generated ${result.invoicesCreated} invoices for ${request.fromMonth} to ${request.toMonth}",
            metadata = mapOf(
                "invoicesCreated" to result.invoicesCreated,
                "ledgerEntriesAssigned" to result.ledgerEntriesAssigned,
                "totalAmount" to result.totalAmount,
                "sendEmail" to request.sendEmail
            )
        )
        return ResponseEntity.ok(result)
    }

    @GetMapping("/suggested-period")
    fun getSuggestedPeriod(): ResponseEntity<InvoiceSuggestion> {
        return ResponseEntity.ok(invoiceService.getSuggestedInvoicePeriod())
    }

    @GetMapping
    fun getInvoices(
        @RequestParam(required = false) subscriberId: UUID?
    ): ResponseEntity<List<InvoiceResponse>> {
        return ResponseEntity.ok(invoiceService.getInvoices(subscriberId))
    }

    @PostMapping("/filter")
    fun filterInvoices(
        @RequestBody filter: InvoiceFilterRequest
    ): ResponseEntity<List<InvoiceResponse>> {
        return ResponseEntity.ok(invoiceService.getInvoicesWithFilters(filter))
    }

    @GetMapping("/{invoiceId}")
    fun getInvoice(
        @PathVariable invoiceId: UUID
    ): ResponseEntity<InvoiceDetailResponse> {
        return ResponseEntity.ok(invoiceService.getInvoice(invoiceId))
    }

    @GetMapping("/{invoiceId}/status-history")
    fun getInvoiceStatusHistory(
        @PathVariable invoiceId: UUID
    ): ResponseEntity<List<InvoiceStatusHistoryResponse>> {
        return ResponseEntity.ok(adminActionService.getInvoiceStatusHistory(invoiceId))
    }

    @GetMapping("/{invoiceId}/pdf")
    fun getInvoicePdf(
        @PathVariable invoiceId: UUID
    ): ResponseEntity<ByteArrayResource> {
        val pdfBytes = invoiceService.generateInvoicePdf(invoiceId)

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "attachment; filename=\"invoice-$invoiceId.pdf\"")
            .body(ByteArrayResource(pdfBytes))
    }

    @PatchMapping("/{invoiceId}/mark-paid")
    fun markInvoiceAsPaid(
        @PathVariable invoiceId: UUID,
        authentication: Authentication
    ): ResponseEntity<InvoiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invoiceBefore = invoiceService.getInvoice(invoiceId).invoice
        val updatedInvoice = invoiceService.markAsPaid(invoiceId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.INVOICE_MARKED_PAID,
            targetType = AdminActionTargetType.INVOICE,
            targetId = invoiceId,
            subscriberId = updatedInvoice.subscriberId,
            fromMonth = updatedInvoice.fromMonth,
            toMonth = updatedInvoice.toMonth,
            summary = "Marked invoice $invoiceId as paid",
            metadata = mapOf(
                "statusBefore" to invoiceBefore.status,
                "statusAfter" to updatedInvoice.status,
                "totalAmount" to updatedInvoice.totalAmount
            )
        )
        return ResponseEntity.ok(updatedInvoice)
    }

    @PatchMapping("/{invoiceId}/status")
    fun updateInvoiceStatus(
        @PathVariable invoiceId: UUID,
        @RequestBody request: InvoiceStatusUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<InvoiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invoiceBefore = invoiceService.getInvoice(invoiceId).invoice
        val requestedStatus = InvoiceStatus.valueOf(request.status.uppercase())
        val updatedInvoice = invoiceService.updateStatus(invoiceId, requestedStatus)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.INVOICE_STATUS_UPDATED,
            targetType = AdminActionTargetType.INVOICE,
            targetId = invoiceId,
            subscriberId = updatedInvoice.subscriberId,
            fromMonth = updatedInvoice.fromMonth,
            toMonth = updatedInvoice.toMonth,
            summary = "Changed invoice $invoiceId status from ${invoiceBefore.status} to ${updatedInvoice.status}",
            metadata = mapOf(
                "statusBefore" to invoiceBefore.status,
                "statusAfter" to updatedInvoice.status,
                "totalAmount" to updatedInvoice.totalAmount
            )
        )
        return ResponseEntity.ok(updatedInvoice)
    }

    @PostMapping("/manual")
    fun createManualInvoice(
        @Valid @RequestBody request: ManualInvoiceCreateRequest,
        authentication: Authentication
    ): ResponseEntity<InvoiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invoice = invoiceService.createManualInvoice(request, performedByAccountId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.MANUAL_INVOICE_CREATED,
            targetType = AdminActionTargetType.INVOICE,
            targetId = invoice.id,
            subscriberId = invoice.subscriberId,
            fromMonth = invoice.fromMonth,
            toMonth = invoice.toMonth,
            summary = "Created manual invoice for ${invoice.subscriberName}",
            metadata = mapOf(
                "totalAmount" to invoice.totalAmount,
                "sendEmail" to request.sendEmail
            )
        )
        return ResponseEntity.ok(invoice)
    }

    @PostMapping("/{invoiceId}/duplicate")
    fun duplicateInvoice(
        @PathVariable invoiceId: UUID,
        @Valid @RequestBody request: InvoiceDuplicateRequest,
        authentication: Authentication
    ): ResponseEntity<InvoiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invoice = invoiceService.duplicateInvoice(invoiceId, request, performedByAccountId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.INVOICE_DUPLICATED,
            targetType = AdminActionTargetType.INVOICE,
            targetId = invoice.id,
            subscriberId = invoice.subscriberId,
            fromMonth = invoice.fromMonth,
            toMonth = invoice.toMonth,
            summary = "Duplicated invoice $invoiceId for ${invoice.subscriberName}",
            metadata = mapOf(
                "sourceInvoiceId" to invoiceId,
                "totalAmount" to invoice.totalAmount,
                "sendEmail" to request.sendEmail
            )
        )
        return ResponseEntity.ok(invoice)
    }

    @PostMapping("/{invoiceId}/pay-from-balance")
    fun payFromBalance(
        @PathVariable invoiceId: UUID,
        authentication: Authentication
    ): ResponseEntity<InvoiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invoiceBefore = invoiceService.getInvoice(invoiceId).invoice
        val updatedInvoice = invoiceService.payFromBalance(invoiceId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.INVOICE_PAID_FROM_BALANCE,
            targetType = AdminActionTargetType.INVOICE,
            targetId = invoiceId,
            subscriberId = updatedInvoice.subscriberId,
            fromMonth = updatedInvoice.fromMonth,
            toMonth = updatedInvoice.toMonth,
            summary = "Paid invoice $invoiceId from subscriber balance",
            metadata = mapOf(
                "statusBefore" to invoiceBefore.status,
                "statusAfter" to updatedInvoice.status,
                "totalAmount" to updatedInvoice.totalAmount
            )
        )
        return ResponseEntity.ok(updatedInvoice)
    }

    @PostMapping("/drafts/pay-from-balance")
    fun payDraftInvoicesFromBalance(authentication: Authentication): ResponseEntity<InvoiceBulkBalancePaymentResult> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val result = invoiceService.payDraftInvoicesFromBalance()
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.INVOICE_BULK_PAID_FROM_BALANCE,
            targetType = AdminActionTargetType.INVOICE,
            summary = "Paid ${result.paidCount} of ${result.attemptedCount} draft invoices from subscriber balances",
            metadata = mapOf(
                "attemptedCount" to result.attemptedCount,
                "paidCount" to result.paidCount,
                "skippedCount" to result.skippedCount,
                "failedCount" to result.failedCount,
                "totalPaidAmount" to result.totalPaidAmount,
                "paidInvoiceIds" to result.items.filter { it.paid }.map { it.invoiceId },
                "skippedInvoiceIds" to result.items.filter { it.skipped }.map { it.invoiceId },
                "failedInvoiceIds" to result.items.filter { !it.paid && !it.skipped }.map { it.invoiceId }
            )
        )
        return ResponseEntity.ok(result)
    }

    @PostMapping("/{invoiceId}/email")
    fun sendInvoiceEmail(
        @PathVariable invoiceId: UUID,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invoiceBefore = invoiceService.getInvoice(invoiceId).invoice
        val success = invoiceService.sendInvoiceEmail(invoiceId)
        if (success) {
            val invoice = invoiceService.getInvoice(invoiceId).invoice
            adminAuditLogService.log(
                createdByAccountId = performedByAccountId,
                actionType = AdminActionType.INVOICE_EMAIL_SENT,
                targetType = AdminActionTargetType.INVOICE,
                targetId = invoiceId,
                subscriberId = invoice.subscriberId,
                fromMonth = invoice.fromMonth,
                toMonth = invoice.toMonth,
                summary = "Sent invoice email for invoice $invoiceId",
                metadata = mapOf(
                    "totalAmount" to invoice.totalAmount,
                    "statusBefore" to invoiceBefore.status,
                    "statusAfter" to invoice.status
                )
            )
        }
        return if (success) {
            ResponseEntity.ok(mapOf("message" to "Invoice email sent successfully"))
        } else {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send invoice email")
        }
    }

    @PostMapping("/drafts/email")
    fun sendDraftInvoiceEmails(authentication: Authentication): ResponseEntity<InvoiceBulkEmailResult> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val result = invoiceService.sendDraftInvoiceEmails()
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.INVOICE_BULK_EMAIL_SENT,
            targetType = AdminActionTargetType.INVOICE,
            summary = "Sent ${result.sentCount} of ${result.attemptedCount} draft invoice emails",
            metadata = mapOf(
                "attemptedCount" to result.attemptedCount,
                "sentCount" to result.sentCount,
                "updatedCount" to result.updatedCount,
                "failedCount" to result.failedCount,
                "dryRun" to result.dryRun,
                "invoiceIds" to result.items.map { it.invoiceId },
                "failedInvoiceIds" to result.items.filter { !it.sent }.map { it.invoiceId }
            )
        )
        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/{invoiceId}")
    fun deleteInvoice(
        @PathVariable invoiceId: UUID,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invoice = invoiceService.getInvoice(invoiceId).invoice
        val message = invoiceService.deleteInvoice(invoiceId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.INVOICE_DELETED,
            targetType = AdminActionTargetType.INVOICE,
            targetId = invoiceId,
            subscriberId = invoice.subscriberId,
            fromMonth = invoice.fromMonth,
            toMonth = invoice.toMonth,
            summary = message,
            metadata = mapOf(
                "origin" to invoice.origin,
                "status" to invoice.status,
                "totalAmount" to invoice.totalAmount
            )
        )
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @PatchMapping("/{invoiceId}/notes")
    fun updateInvoiceNotes(
        @PathVariable invoiceId: UUID,
        @RequestBody request: InvoiceNotesUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<InvoiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val updatedInvoice = invoiceService.updateInvoiceNotes(invoiceId, request.notes)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.INVOICE_NOTES_UPDATED,
            targetType = AdminActionTargetType.INVOICE,
            targetId = invoiceId,
            subscriberId = updatedInvoice.subscriberId,
            fromMonth = updatedInvoice.fromMonth,
            toMonth = updatedInvoice.toMonth,
            summary = "Updated notes for invoice $invoiceId"
        )
        return ResponseEntity.ok(updatedInvoice)
    }

    @PatchMapping("/{invoiceId}/void")
    fun voidInvoice(
        @PathVariable invoiceId: UUID,
        @RequestBody(required = false) request: InvoiceVoidRequest?,
        authentication: Authentication
    ): ResponseEntity<InvoiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invoiceBefore = invoiceService.getInvoice(invoiceId).invoice
        val updatedInvoice = invoiceService.voidInvoice(invoiceId, request?.reason)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.INVOICE_VOIDED,
            targetType = AdminActionTargetType.INVOICE,
            targetId = invoiceId,
            subscriberId = updatedInvoice.subscriberId,
            fromMonth = updatedInvoice.fromMonth,
            toMonth = updatedInvoice.toMonth,
            summary = "Voided invoice $invoiceId",
            metadata = mapOf(
                "origin" to updatedInvoice.origin,
                "statusBefore" to invoiceBefore.status,
                "statusAfter" to updatedInvoice.status,
                "totalAmount" to updatedInvoice.totalAmount,
                "reason" to request?.reason
            )
        )
        return ResponseEntity.ok(updatedInvoice)
    }
}
