package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.InvoiceDetailResponse
import com.almonium.famsubbe.dto.InvoiceFilterRequest
import com.almonium.famsubbe.dto.InvoiceGenerationRequest
import com.almonium.famsubbe.dto.InvoiceGenerationResult
import com.almonium.famsubbe.dto.InvoiceNotesUpdateRequest
import com.almonium.famsubbe.dto.InvoiceResponse
import com.almonium.famsubbe.dto.InvoiceSuggestion
import com.almonium.famsubbe.dto.InvoiceVoidRequest
import com.almonium.famsubbe.dto.ManualInvoiceCreateRequest
import com.almonium.famsubbe.dto.OutstandingBalanceInvoiceRequest
import com.almonium.famsubbe.entity.AdminActionTargetType
import com.almonium.famsubbe.entity.AdminActionType
import com.almonium.famsubbe.service.AccountService
import com.almonium.famsubbe.service.AdminAuditLogService
import com.almonium.famsubbe.service.InvoiceService
import com.almonium.famsubbe.util.AuthenticationUtil
import jakarta.validation.Valid
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
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
            metadata = mapOf("totalAmount" to updatedInvoice.totalAmount)
        )
        return ResponseEntity.ok(updatedInvoice)
    }

    @PostMapping("/outstanding-balance")
    fun generateOutstandingBalanceInvoice(
        @RequestBody request: OutstandingBalanceInvoiceRequest,
        authentication: Authentication
    ): ResponseEntity<InvoiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invoice = invoiceService.generateOutstandingBalanceInvoice(request, performedByAccountId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.OUTSTANDING_BALANCE_INVOICE_CREATED,
            targetType = AdminActionTargetType.INVOICE,
            targetId = invoice.id,
            subscriberId = invoice.subscriberId,
            fromMonth = invoice.fromMonth,
            toMonth = invoice.toMonth,
            summary = "Created outstanding balance invoice for ${invoice.subscriberName}",
            metadata = mapOf(
                "totalAmount" to invoice.totalAmount,
                "sendEmail" to request.sendEmail
            )
        )
        return ResponseEntity.ok(invoice)
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

    @PostMapping("/{invoiceId}/pay-from-balance")
    fun payFromBalance(
        @PathVariable invoiceId: UUID,
        authentication: Authentication
    ): ResponseEntity<InvoiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
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
            metadata = mapOf("totalAmount" to updatedInvoice.totalAmount)
        )
        return ResponseEntity.ok(updatedInvoice)
    }

    @PostMapping("/{invoiceId}/email")
    fun sendInvoiceEmail(
        @PathVariable invoiceId: UUID,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
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
                    "status" to invoice.status
                )
            )
        }
        return if (success) {
            ResponseEntity.ok(mapOf("message" to "Invoice email sent successfully"))
        } else {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to send invoice email"))
        }
    }

    @DeleteMapping("/{invoiceId}")
    fun deleteInvoice(
        @PathVariable invoiceId: UUID,
        @RequestParam(required = false, defaultValue = "true") addToBalance: Boolean,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invoice = invoiceService.getInvoice(invoiceId).invoice
        val message = invoiceService.deleteInvoice(invoiceId, addToBalance)
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
                "totalAmount" to invoice.totalAmount,
                "addToBalance" to addToBalance
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
                "totalAmount" to updatedInvoice.totalAmount,
                "reason" to request?.reason
            )
        )
        return ResponseEntity.ok(updatedInvoice)
    }
}
