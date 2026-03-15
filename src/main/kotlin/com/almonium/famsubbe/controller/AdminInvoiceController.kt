package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.*
import com.almonium.famsubbe.service.AccountService
import com.almonium.famsubbe.service.InvoiceService
import com.almonium.famsubbe.util.AuthenticationUtil
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/admin/invoices")
class AdminInvoiceController(
    private val invoiceService: InvoiceService,
    private val accountService: AccountService
) {

    @PostMapping("/generate")
    fun generateInvoices(
        @RequestBody request: InvoiceGenerationRequest,
        authentication: Authentication
    ): ResponseEntity<InvoiceGenerationResult> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val result = invoiceService.generateInvoices(request, performedByAccountId)
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
        @PathVariable invoiceId: UUID
    ): ResponseEntity<InvoiceResponse> {
        val updatedInvoice = invoiceService.markAsPaid(invoiceId)
        return ResponseEntity.ok(updatedInvoice)
    }

    @PostMapping("/outstanding-balance")
    fun generateOutstandingBalanceInvoice(
        @RequestBody request: OutstandingBalanceInvoiceRequest,
        authentication: Authentication
    ): ResponseEntity<InvoiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invoice = invoiceService.generateOutstandingBalanceInvoice(request, performedByAccountId)
        return ResponseEntity.ok(invoice)
    }

    @PostMapping("/{invoiceId}/pay-from-balance")
    fun payFromBalance(
        @PathVariable invoiceId: UUID
    ): ResponseEntity<InvoiceResponse> {
        val updatedInvoice = invoiceService.payFromBalance(invoiceId)
        return ResponseEntity.ok(updatedInvoice)
    }

    @PostMapping("/{invoiceId}/email")
    fun sendInvoiceEmail(
        @PathVariable invoiceId: UUID
    ): ResponseEntity<Map<String, String>> {
        val success = invoiceService.sendInvoiceEmail(invoiceId)
        return if (success) {
            ResponseEntity.ok(mapOf("message" to "Invoice email sent successfully"))
        } else {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to send invoice email"))
        }
    }
}