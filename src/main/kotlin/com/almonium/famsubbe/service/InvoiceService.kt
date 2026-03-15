package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.*
import com.almonium.famsubbe.entity.Invoice
import com.almonium.famsubbe.entity.InvoiceStatus
import com.almonium.famsubbe.entity.LedgerEntry
import com.almonium.famsubbe.repository.InvoiceRepository
import com.almonium.famsubbe.repository.LedgerEntryRepository
import com.almonium.famsubbe.repository.SubscriberRepository
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@Service
@Transactional
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val subscriberRepository: SubscriberRepository
) {
    fun generateInvoices(
        request: InvoiceGenerationRequest,
        performedByAccountId: UUID
    ): InvoiceGenerationResult {
        require(request.fromMonth <= request.toMonth) { "fromMonth cannot be after toMonth" }

        val entries = if (request.subscriberId != null) {
            ledgerEntryRepository.findUninvoicedBySubscriberAndPeriod(
                request.subscriberId,
                request.fromMonth,
                request.toMonth
            )
        } else {
            ledgerEntryRepository.findUninvoicedByPeriod(
                request.fromMonth,
                request.toMonth
            )
        }

        check(entries.isNotEmpty()) { "No uninvoiced ledger entries found in the selected period" }

        val now = Instant.now()
        val grouped = entries.groupBy { requireNotNull(it.subscriber) }

        val createdInvoices = mutableListOf<Invoice>()
        var totalAssigned = 0
        var totalAmount = BigDecimal.ZERO
        val items = mutableListOf<InvoiceGenerationItemResult>()

        for ((subscriber, subscriberEntries) in grouped) {
            val invoiceTotal = subscriberEntries
                .map { requireNotNull(it.amount) }
                .fold(BigDecimal.ZERO, BigDecimal::add)

            val invoice = invoiceRepository.save(
                Invoice().apply {
                    this.subscriber = subscriber
                    this.fromMonth = request.fromMonth
                    this.toMonth = request.toMonth
                    this.totalAmount = invoiceTotal
                    this.status = if (request.sendEmail) InvoiceStatus.SENT else InvoiceStatus.DRAFT
                    this.createdAt = now
                    this.createdByAccountId = performedByAccountId
                    this.sentAt = if (request.sendEmail) now else null
                    this.emailSent = request.sendEmail
                    this.notes = "generated_by=$performedByAccountId"
                }
            )

            subscriberEntries.forEach { it.invoice = invoice }
            ledgerEntryRepository.saveAll(subscriberEntries)

            createdInvoices += invoice
            totalAssigned += subscriberEntries.size
            totalAmount = totalAmount.add(invoiceTotal)

            items += InvoiceGenerationItemResult(
                invoiceId = requireNotNull(invoice.id),
                subscriberId = requireNotNull(subscriber.id),
                subscriberName = requireNotNull(subscriber.name),
                fromMonth = request.fromMonth,
                toMonth = request.toMonth,
                totalAmount = invoiceTotal,
                ledgerEntryCount = subscriberEntries.size,
                emailRequested = request.sendEmail,
                emailSent = request.sendEmail,
                message = "Created invoice with ${subscriberEntries.size} ledger entries"
            )
        }

        return InvoiceGenerationResult(
            invoicesCreated = createdInvoices.size,
            ledgerEntriesAssigned = totalAssigned,
            totalAmount = totalAmount,
            items = items
        )
    }

    @Transactional(readOnly = true)
    fun getInvoice(invoiceId: UUID): InvoiceDetailResponse {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }
        
        val entries = ledgerEntryRepository.findByInvoiceId(invoiceId)
        
        return InvoiceDetailResponse(
            invoice = invoice.toResponse(),
            entries = entries.map { it.toInvoiceLedgerEntryResponse() }
        )
    }

    @Transactional(readOnly = true)
    fun getInvoices(subscriberId: UUID?): List<InvoiceResponse> {
        return invoiceRepository.findAllFiltered(subscriberId)
            .map { it.toResponse() }
    }
    
    @Transactional(readOnly = true)
    fun getSuggestedInvoicePeriod(): InvoiceSuggestion {
        val lastInvoice = invoiceRepository.findFirstByOrderByToMonthDesc()
        val lastInvoicedToMonth = lastInvoice?.toMonth
        
        val oldestUninvoicedMonth = ledgerEntryRepository.findOldestUninvoicedMonth()
        val latestUninvoicedMonth = ledgerEntryRepository.findLatestUninvoicedMonth()
        
        val suggestedFromMonth = oldestUninvoicedMonth
        val suggestedToMonth = latestUninvoicedMonth

        return InvoiceSuggestion(
            lastInvoicedToMonth = lastInvoicedToMonth,
            suggestedFromMonth = suggestedFromMonth,
            suggestedToMonth = suggestedToMonth
        )
    }

    private fun Invoice.toResponse(): InvoiceResponse =
        InvoiceResponse(
            id = requireNotNull(id),
            subscriberId = requireNotNull(subscriber?.id),
            subscriberName = requireNotNull(subscriber?.name),
            fromMonth = requireNotNull(fromMonth),
            toMonth = requireNotNull(toMonth),
            totalAmount = requireNotNull(totalAmount),
            status = status.name,
            createdAt = requireNotNull(createdAt),
            createdByAccountId = requireNotNull(createdByAccountId),
            sentAt = sentAt,
            emailSent = emailSent,
            notes = notes
        )

    private fun LedgerEntry.toInvoiceLedgerEntryResponse(): InvoiceLedgerEntryResponse =
        InvoiceLedgerEntryResponse(
            ledgerEntryId = requireNotNull(id),
            chargeId = requireNotNull(charge?.id),
            subscriptionServiceId = requireNotNull(subscriptionService?.id),
            subscriptionServiceName = requireNotNull(subscriptionService?.name),
            recordedMonth = requireNotNull(recordedMonth),
            amount = requireNotNull(amount),
            participantCount = participantCount,
            calculatedAt = requireNotNull(calculatedAt)
        )

    fun markAsPaid(invoiceId: UUID): InvoiceResponse {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }
        
        check(invoice.status != InvoiceStatus.PAID) { "Invoice is already marked as paid" }
        
        invoice.status = InvoiceStatus.PAID
        val updatedInvoice = invoiceRepository.save(invoice)
        
        return updatedInvoice.toResponse()
    }

    fun generateInvoicePdf(invoiceId: UUID): ByteArray {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        val entries = ledgerEntryRepository.findByInvoiceId(invoiceId)

        val outputStream = ByteArrayOutputStream()
        val writer = PdfWriter(outputStream)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)

        // Put this font file into src/main/resources/fonts/
        // For example: DejaVuSans.ttf or NotoSans-Regular.ttf
        val fontStream = javaClass.classLoader.getResource("fonts/NotoSans.ttf")
            ?: throw IllegalStateException("Font file not found")

        val font: PdfFont = PdfFontFactory.createFont(
            fontStream.toURI().path,
            com.itextpdf.io.font.PdfEncodings.IDENTITY_H,
            PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
        )

        document.add(Paragraph("Invoice #${invoice.id}").setFont(font))
        document.add(Paragraph("Subscriber: ${invoice.subscriber?.name ?: ""}").setFont(font))
        document.add(Paragraph("Period: ${invoice.fromMonth} - ${invoice.toMonth}").setFont(font))
        document.add(Paragraph("Total: ₴${invoice.totalAmount}").setFont(font))
        document.add(Paragraph(" ").setFont(font))

        document.add(Paragraph("Ledger Entries:").setFont(font))
        entries.forEach { entry ->
            document.add(
                Paragraph("- ${entry.subscriptionService?.name ?: ""}: ₴${entry.amount} (${entry.recordedMonth})")
                    .setFont(font)
            )
        }

        document.close()
        return outputStream.toByteArray()
    }
}