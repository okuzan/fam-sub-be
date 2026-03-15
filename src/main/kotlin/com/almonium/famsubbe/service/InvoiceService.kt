package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.*
import com.almonium.famsubbe.entity.*
import com.almonium.famsubbe.repository.InvoiceRepository
import com.almonium.famsubbe.repository.LedgerEntryRepository
import com.almonium.famsubbe.repository.MembershipRepository
import com.almonium.famsubbe.repository.SubscriberRepository
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

@Service
@Transactional
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val subscriberRepository: SubscriberRepository,
    private val membershipRepository: MembershipRepository
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
                    this.origin = InvoiceOrigin.SUBSCRIPTION_LEDGER
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
            notes = notes,
            origin = origin.name
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

    fun generateOutstandingBalanceInvoice(
        request: OutstandingBalanceInvoiceRequest,
        performedByAccountId: UUID
    ): InvoiceResponse {
        val subscriber = subscriberRepository.findById(request.subscriberId)
            .orElseThrow { IllegalArgumentException("Subscriber not found: ${request.subscriberId}") }

        // Check if subscriber has negative balance
        val currentBalance = subscriber.balance?: BigDecimal.ZERO
        check(currentBalance < BigDecimal.ZERO) {
            "Subscriber must have negative balance to generate outstanding balance invoice. Current balance: $currentBalance" 
        }

        // Calculate the amount needed to zero the balance (absolute value of negative balance)
        val zeroingAmount = currentBalance.abs()
        
        val now = Instant.now()
        
        // Create invoice with zeroing amount
        val invoice = invoiceRepository.save(
            Invoice().apply {
                this.subscriber = subscriber
                this.fromMonth = YearMonth.now()
                this.toMonth = YearMonth.now()
                this.totalAmount = zeroingAmount
                this.status = if (request.sendEmail) InvoiceStatus.SENT else InvoiceStatus.DRAFT
                this.createdAt = now
                this.createdByAccountId = performedByAccountId
                this.sentAt = if (request.sendEmail) now else null
                this.emailSent = request.sendEmail
                this.notes = request.notes ?: "outstanding_balance_invoice_generated_by=$performedByAccountId"
                this.origin = InvoiceOrigin.OUTSTANDING_BALANCE
            }
        )
        subscriber.balance = BigDecimal.ZERO
        subscriberRepository.save(subscriber)
        return invoice.toResponse()
    }

    fun payFromBalance(invoiceId: UUID): InvoiceResponse {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }
        
        check(invoice.status != InvoiceStatus.PAID) { "Invoice is already marked as paid" }
        
        val subscriber = requireNotNull(invoice.subscriber)
        val currentBalance = subscriber.balance ?: BigDecimal.ZERO
        val invoiceAmount = requireNotNull(invoice.totalAmount)
        
        check(currentBalance >= invoiceAmount) { 
            "Insufficient balance. Current balance: $currentBalance, Invoice amount: $invoiceAmount" 
        }
        
        // Deduct from subscriber balance
        subscriber.balance = currentBalance.subtract(invoiceAmount)
        subscriberRepository.save(subscriber)
        
        // Mark invoice as paid
        invoice.status = InvoiceStatus.PAID
        val updatedInvoice = invoiceRepository.save(invoice)
        
        return updatedInvoice.toResponse()
    }

    @Transactional(readOnly = true)
    fun getInvoicesWithFilters(filter: InvoiceFilterRequest): List<InvoiceResponse> {
        val status = filter.status?.let { InvoiceStatus.valueOf(it.uppercase()) }
        val origin = filter.origin?.let { InvoiceOrigin.valueOf(it.uppercase()) }

        val spec = Specification<Invoice> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            filter.subscriberId?.let {
                predicates += cb.equal(root.get<Subscriber>("subscriber").get<UUID>("id"), it)
            }

            status?.let {
                predicates += cb.equal(root.get<InvoiceStatus>("status"), it)
            }

            filter.dateFrom?.let {
                predicates += cb.greaterThanOrEqualTo(root.get("createdAt"), it)
            }

            filter.dateTo?.let {
                predicates += cb.lessThanOrEqualTo(root.get("createdAt"), it)
            }

            origin?.let {
                predicates += cb.equal(root.get<InvoiceOrigin>("origin"), it)
            }

            cb.and(*predicates.toTypedArray())
        }

        return invoiceRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"))
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getSubscriberDetails(subscriberId: UUID): SubscriberDetailResponse {
        val subscriber = subscriberRepository.findById(subscriberId)
            .orElseThrow { NoSuchElementException("Subscriber not found with id: $subscriberId") }

        // Get active subscriptions (memberships that are currently active)
        val currentMonth = YearMonth.now()
        val activeMemberships = membershipRepository.findActiveBySubscriberAndMonth(subscriberId, currentMonth)
        
        val activeSubscriptions = activeMemberships.map { membership ->
            ActiveSubscriptionDto(
                id = membership.id!!,
                serviceName = membership.subscriptionService?.name ?: "",
                servicePrice = membership.subscriptionService?.price ?: BigDecimal.ZERO,
                startMonth = membership.startMonth?.toString() ?: "",
                endMonth = membership.endMonth?.toString()
            )
        }

        // Get unpaid invoices (DRAFT and SENT status)
        val unpaidInvoices = invoiceRepository.findAll(
            { root, _, cb ->
                val predicates = mutableListOf<Predicate>()
                predicates.add(cb.equal(root.get<Subscriber>("subscriber").get<UUID>("id"), subscriberId))
                predicates.add(cb.notEqual(root.get<InvoiceStatus>("status"), InvoiceStatus.PAID))
                cb.and(*predicates.toTypedArray())
            },
            Sort.by(Sort.Direction.DESC, "createdAt")
        )

        val unpaidInvoicesDto = unpaidInvoices.map { invoice ->
            UnpaidInvoiceDto(
                id = invoice.id!!,
                totalAmount = invoice.totalAmount ?: BigDecimal.ZERO,
                fromMonth = invoice.fromMonth?.toString() ?: "",
                toMonth = invoice.toMonth?.toString() ?: "",
                createdAt = invoice.createdAt ?: Instant.now(),
                status = invoice.status.name,
                notes = invoice.notes
            )
        }

        // Calculate total amount owed (sum of unpaid invoices minus balance)
        val totalUnpaidAmount = unpaidInvoices
            .map { it.totalAmount ?: BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, amount -> acc + amount }
        
        val totalAmountOwed = totalUnpaidAmount - (subscriber.balance ?: BigDecimal.ZERO)

        return SubscriberDetailResponse(
            id = subscriber.id!!,
            name = subscriber.name ?: "",
            email = subscriber.email ?: "",
            balance = subscriber.balance ?: BigDecimal.ZERO,
            totalAmountOwed = totalAmountOwed,
            activeSubscriptions = activeSubscriptions,
            unpaidInvoices = unpaidInvoicesDto
        )
    }
}