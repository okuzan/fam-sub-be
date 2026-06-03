package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.ActiveSubscriptionDto
import com.almonium.famsubbe.dto.InvoiceBulkBalancePaymentItemResult
import com.almonium.famsubbe.dto.InvoiceBulkBalancePaymentResult
import com.almonium.famsubbe.dto.InvoiceBulkEmailItemResult
import com.almonium.famsubbe.dto.InvoiceBulkEmailResult
import com.almonium.famsubbe.dto.InvoiceDetailResponse
import com.almonium.famsubbe.dto.InvoiceFilterRequest
import com.almonium.famsubbe.dto.InvoiceGenerationItemResult
import com.almonium.famsubbe.dto.InvoiceGenerationRequest
import com.almonium.famsubbe.dto.InvoiceGenerationResult
import com.almonium.famsubbe.dto.InvoiceLedgerEntryResponse
import com.almonium.famsubbe.dto.InvoiceResponse
import com.almonium.famsubbe.dto.InvoiceSuggestion
import com.almonium.famsubbe.dto.ManualInvoiceCreateRequest
import com.almonium.famsubbe.dto.SubscriberDetailResponse
import com.almonium.famsubbe.dto.SubscriberDebtPaymentItemResult
import com.almonium.famsubbe.dto.SubscriberDebtPaymentResult
import com.almonium.famsubbe.dto.UnpaidInvoiceDto
import com.almonium.famsubbe.dto.WeeklySituationInvoiceDto
import com.almonium.famsubbe.dto.WeeklySituationLedgerEntryDto
import com.almonium.famsubbe.entity.Invoice
import com.almonium.famsubbe.entity.InvoiceGenerationRun
import com.almonium.famsubbe.entity.InvoiceOrigin
import com.almonium.famsubbe.entity.InvoiceStatus
import com.almonium.famsubbe.entity.LedgerEntry
import com.almonium.famsubbe.entity.Subscriber
import com.almonium.famsubbe.repository.InvoiceGenerationRunRepository
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
import org.springframework.beans.factory.annotation.Value
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
    private val invoiceGenerationRunRepository: InvoiceGenerationRunRepository,
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val subscriberRepository: SubscriberRepository,
    private val membershipRepository: MembershipRepository,
    private val invoiceEmailService: InvoiceEmailService,
    @Value($$"${app.email.dry-run:false}") private val isDryRun: Boolean
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
        val generationRun = invoiceGenerationRunRepository.save(
            InvoiceGenerationRun().apply {
                this.fromMonth = request.fromMonth
                this.toMonth = request.toMonth
                this.subscriberId = request.subscriberId
                this.sendEmail = request.sendEmail
                this.createdAt = now
                this.createdByAccountId = performedByAccountId
            }
        )
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
                    val autoPaid = subscriber.autoPayInvoices
                    this.subscriber = subscriber
                    this.fromMonth = request.fromMonth
                    this.toMonth = request.toMonth
                    this.totalAmount = invoiceTotal
                    this.status = initialInvoiceStatus(subscriber, request.sendEmail)
                    this.createdAt = now
                    this.statusChangedAt = now
                    this.createdByAccountId = performedByAccountId
                    this.sentAt = if (!autoPaid && request.sendEmail) now else null
                    this.emailSent = !autoPaid && request.sendEmail
                    this.notes = "Auto-paid by subscriber setting".takeIf { autoPaid }
                    this.origin = InvoiceOrigin.SUBSCRIPTION_LEDGER
                    this.invoiceGenerationRun = generationRun
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
                emailSent = invoice.emailSent,
                message = if (invoice.status == InvoiceStatus.PAID) {
                    "Created auto-paid invoice with ${subscriberEntries.size} ledger entries"
                } else {
                    "Created invoice with ${subscriberEntries.size} ledger entries"
                }
            )
        }

        return InvoiceGenerationResult(
            runId = requireNotNull(generationRun.id),
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
        val lastInvoice = invoiceRepository.findFirstByOriginOrderByToMonthDesc(InvoiceOrigin.SUBSCRIPTION_LEDGER)
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
            statusChangedAt = requireNotNull(statusChangedAt),
            createdByAccountId = requireNotNull(createdByAccountId),
            invoiceGenerationRunId = invoiceGenerationRun?.id,
            sentAt = sentAt,
            emailSent = emailSent,
            notes = notes,
            origin = origin.name
        )

    private fun initialInvoiceStatus(subscriber: Subscriber, sendEmail: Boolean): InvoiceStatus =
        when {
            subscriber.autoPayInvoices -> InvoiceStatus.PAID
            sendEmail -> InvoiceStatus.SENT
            else -> InvoiceStatus.DRAFT
        }

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
        return updateStatus(invoiceId, InvoiceStatus.PAID)
    }

    fun updateStatus(invoiceId: UUID, status: InvoiceStatus): InvoiceResponse {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        invoice.setStatus(status)
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
        document.add(Paragraph("Origin: ${invoice.origin.name}").setFont(font))
        document.add(Paragraph("Total: ₴${invoice.totalAmount}").setFont(font))
        invoice.notes?.takeIf { it.isNotBlank() }?.let {
            document.add(Paragraph("Notes: $it").setFont(font))
        }
        document.add(Paragraph(" ").setFont(font))

        if (entries.isEmpty()) {
            document.add(Paragraph("No ledger entries attached to this invoice.").setFont(font))
        } else {
            document.add(Paragraph("Ledger Entries:").setFont(font))
            entries.forEach { entry ->
                document.add(
                    Paragraph("- ${entry.subscriptionService?.name ?: ""}: ₴${entry.amount} (${entry.recordedMonth})")
                        .setFont(font)
                )
            }
        }

        document.close()
        return outputStream.toByteArray()
    }

    fun createManualInvoice(
        request: ManualInvoiceCreateRequest,
        performedByAccountId: UUID
    ): InvoiceResponse {
        val subscriber = subscriberRepository.findById(request.subscriberId)
            .orElseThrow { IllegalArgumentException("Subscriber not found: ${request.subscriberId}") }

        val now = Instant.now()
        val invoice = invoiceRepository.save(
            Invoice().apply {
                val autoPaid = subscriber.autoPayInvoices
                this.subscriber = subscriber
                this.fromMonth = request.invoiceMonth
                this.toMonth = request.invoiceMonth
                this.totalAmount = request.amount
                this.status = initialInvoiceStatus(subscriber, request.sendEmail)
                this.createdAt = now
                this.statusChangedAt = now
                this.createdByAccountId = performedByAccountId
                this.sentAt = if (!autoPaid && request.sendEmail) now else null
                this.emailSent = !autoPaid && request.sendEmail
                this.notes = listOfNotNull(
                    request.notes.trim().takeIf { it.isNotBlank() },
                    "Auto-paid by subscriber setting".takeIf { autoPaid }
                ).joinToString("; ").takeIf { it.isNotBlank() }
                this.origin = InvoiceOrigin.MANUAL
            }
        )

        if (request.sendEmail && !subscriber.autoPayInvoices) {
            invoiceEmailService.sendInvoiceEmail(invoice, emptyList(), calculateTotalAmountOwed(subscriber))
        }

        return invoice.toResponse()
    }

    fun payFromBalance(invoiceId: UUID): InvoiceResponse {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        return payInvoiceFromBalance(invoice).toResponse()
    }

    fun payDraftInvoicesFromBalance(): InvoiceBulkBalancePaymentResult {
        val draftInvoices = invoiceRepository.findByStatusOrderByCreatedAtAsc(InvoiceStatus.DRAFT)
        val items = draftInvoices.map { invoice ->
            val invoiceId = requireNotNull(invoice.id)
            val subscriber = requireNotNull(invoice.subscriber)
            val subscriberId = requireNotNull(subscriber.id)
            val subscriberName = requireNotNull(subscriber.name)
            val statusBefore = invoice.status.name
            val invoiceAmount = requireNotNull(invoice.totalAmount)
            val balanceBefore = subscriber.balance ?: BigDecimal.ZERO

            if (balanceBefore < invoiceAmount) {
                InvoiceBulkBalancePaymentItemResult(
                    invoiceId = invoiceId,
                    subscriberId = subscriberId,
                    subscriberName = subscriberName,
                    statusBefore = statusBefore,
                    statusAfter = invoice.status.name,
                    invoiceAmount = invoiceAmount,
                    balanceBefore = balanceBefore,
                    balanceAfter = balanceBefore,
                    paid = false,
                    skipped = true,
                    updated = false,
                    message = "Insufficient balance. Current balance: $balanceBefore, Invoice amount: $invoiceAmount"
                )
            } else {
                val updatedInvoice = payInvoiceFromBalance(invoice)
                val balanceAfter = requireNotNull(updatedInvoice.subscriber).balance ?: BigDecimal.ZERO

                InvoiceBulkBalancePaymentItemResult(
                    invoiceId = invoiceId,
                    subscriberId = subscriberId,
                    subscriberName = subscriberName,
                    statusBefore = statusBefore,
                    statusAfter = updatedInvoice.status.name,
                    invoiceAmount = invoiceAmount,
                    balanceBefore = balanceBefore,
                    balanceAfter = balanceAfter,
                    paid = true,
                    skipped = false,
                    updated = true,
                    message = "Invoice paid from subscriber balance"
                )
            }
        }

        return InvoiceBulkBalancePaymentResult(
            attemptedCount = items.size,
            paidCount = items.count { it.paid },
            skippedCount = items.count { it.skipped },
            failedCount = items.count { !it.paid && !it.skipped },
            totalPaidAmount = items
                .filter { it.paid }
                .map { it.invoiceAmount }
                .fold(BigDecimal.ZERO, BigDecimal::add),
            items = items
        )
    }

    fun payOffSubscriberDebt(subscriberId: UUID, includeCredit: Boolean = true): SubscriberDebtPaymentResult {
        val subscriber = subscriberRepository.findById(subscriberId)
            .orElseThrow { IllegalArgumentException("Subscriber not found: $subscriberId") }
        val balanceBefore = subscriber.balance ?: BigDecimal.ZERO

        val pendingInvoices = invoiceRepository.findBySubscriberAndStatusIn(
            subscriber,
            listOf(InvoiceStatus.DRAFT, InvoiceStatus.SENT)
        ).sortedBy { it.createdAt }

        val items = pendingInvoices.map { invoice ->
            val statusBefore = invoice.status.name
            invoice.setStatus(InvoiceStatus.PAID)
            val updatedInvoice = invoiceRepository.save(invoice)

            SubscriberDebtPaymentItemResult(
                invoiceId = requireNotNull(updatedInvoice.id),
                statusBefore = statusBefore,
                statusAfter = updatedInvoice.status.name,
                invoiceAmount = requireNotNull(updatedInvoice.totalAmount),
                paid = true,
                message = "Invoice marked as paid"
            )
        }

        val creditWrittenOff = if (includeCredit && balanceBefore > BigDecimal.ZERO) {
            subscriber.balance = BigDecimal.ZERO
            subscriberRepository.save(subscriber)
            balanceBefore
        } else {
            BigDecimal.ZERO
        }
        val balanceAfter = subscriber.balance ?: BigDecimal.ZERO

        return SubscriberDebtPaymentResult(
            subscriberId = requireNotNull(subscriber.id),
            subscriberName = requireNotNull(subscriber.name),
            attemptedCount = items.size,
            paidCount = items.count { it.paid },
            totalPaidAmount = items
                .filter { it.paid }
                .map { it.invoiceAmount }
                .fold(BigDecimal.ZERO, BigDecimal::add),
            includeCredit = includeCredit,
            balanceBefore = balanceBefore,
            balance = balanceAfter,
            balanceAfter = balanceAfter,
            creditWrittenOff = creditWrittenOff,
            items = items
        )
    }

    private fun payInvoiceFromBalance(invoice: Invoice): Invoice {
        check(invoice.status != InvoiceStatus.PAID) { "Invoice is already marked as paid" }
        check(invoice.status != InvoiceStatus.VOID) { "Voided invoices cannot be paid from balance" }

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
        invoice.setStatus(InvoiceStatus.PAID)

        return invoiceRepository.save(invoice)
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

    @Transactional
    fun sendInvoiceEmail(invoiceId: UUID): Boolean {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        check(invoice.status != InvoiceStatus.VOID) { "Voided invoices cannot be emailed" }
        
        val entries = ledgerEntryRepository.findByInvoice(invoice)
            .sortedBy { it.recordedMonth }
        val success = invoiceEmailService.sendInvoiceEmail(
            invoice,
            entries,
            calculateTotalAmountOwed(requireNotNull(invoice.subscriber))
        )
        
        if (success && !isDryRun && invoice.status == InvoiceStatus.DRAFT) {
            invoice.emailSent = true
            invoice.sentAt = Instant.now()
            invoice.setStatus(InvoiceStatus.SENT)
            invoiceRepository.save(invoice)
        }
        
        return success
    }

    @Transactional
    fun sendDraftInvoiceEmails(): InvoiceBulkEmailResult {
        val draftInvoices = invoiceRepository.findByStatusOrderByCreatedAtAsc(InvoiceStatus.DRAFT)
        val items = draftInvoices.map { invoice ->
            val invoiceId = requireNotNull(invoice.id)
            val subscriber = requireNotNull(invoice.subscriber)
            val subscriberId = requireNotNull(subscriber.id)
            val subscriberName = requireNotNull(subscriber.name)
            val statusBefore = invoice.status.name

            try {
                val entries = ledgerEntryRepository.findByInvoice(invoice)
                    .sortedBy { it.recordedMonth }
                val success = invoiceEmailService.sendInvoiceEmail(
                    invoice,
                    entries,
                    calculateTotalAmountOwed(subscriber)
                )
                val updated = success && !isDryRun && invoice.status == InvoiceStatus.DRAFT

                if (updated) {
                    invoice.emailSent = true
                    invoice.sentAt = Instant.now()
                    invoice.setStatus(InvoiceStatus.SENT)
                    invoiceRepository.save(invoice)
                }

                InvoiceBulkEmailItemResult(
                    invoiceId = invoiceId,
                    subscriberId = subscriberId,
                    subscriberName = subscriberName,
                    statusBefore = statusBefore,
                    statusAfter = invoice.status.name,
                    sent = success,
                    updated = updated,
                    message = if (success) {
                        "Invoice email sent successfully"
                    } else {
                        "Failed to send invoice email"
                    }
                )
            } catch (e: Exception) {
                InvoiceBulkEmailItemResult(
                    invoiceId = invoiceId,
                    subscriberId = subscriberId,
                    subscriberName = subscriberName,
                    statusBefore = statusBefore,
                    statusAfter = invoice.status.name,
                    sent = false,
                    updated = false,
                    message = e.message ?: "Failed to send invoice email"
                )
            }
        }

        return InvoiceBulkEmailResult(
            attemptedCount = items.size,
            sentCount = items.count { it.sent },
            updatedCount = items.count { it.updated },
            failedCount = items.count { !it.sent },
            dryRun = isDryRun,
            items = items
        )
    }

    fun updateInvoiceNotes(invoiceId: UUID, notes: String?): InvoiceResponse {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        invoice.notes = notes?.trim()?.takeIf { it.isNotBlank() }
        val updatedInvoice = invoiceRepository.save(invoice)

        return updatedInvoice.toResponse()
    }

    fun voidInvoice(invoiceId: UUID, reason: String?): InvoiceResponse {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        check(invoice.status != InvoiceStatus.VOID) { "Invoice is already voided" }
        check(invoice.status != InvoiceStatus.PAID) { "PAID invoices cannot be voided" }

        val normalizedReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedReason != null) {
            invoice.notes = listOfNotNull(invoice.notes, "Voided: $normalizedReason")
                .joinToString("\n")
        }
        invoice.setStatus(InvoiceStatus.VOID)

        return invoiceRepository.save(invoice).toResponse()
    }

    private fun Invoice.setStatus(status: InvoiceStatus) {
        if (this.status != status) {
            this.status = status
            this.statusChangedAt = Instant.now()
        }
    }

    private fun calculateTotalAmountOwed(subscriber: Subscriber): BigDecimal {
        val totalUnpaidAmount = invoiceRepository.findBySubscriberAndStatusIn(
            subscriber,
            listOf(InvoiceStatus.DRAFT, InvoiceStatus.SENT)
        )
            .map { it.totalAmount ?: BigDecimal.ZERO }
            .fold(BigDecimal.ZERO, BigDecimal::add)

        return totalUnpaidAmount - (subscriber.balance ?: BigDecimal.ZERO)
    }

    fun deleteInvoice(invoiceId: UUID, addToBalance: Boolean = true): String {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        check(invoice.status == InvoiceStatus.DRAFT) {
            "Only DRAFT invoices can be deleted"
        }

        check(invoice.origin == InvoiceOrigin.OUTSTANDING_BALANCE || invoice.origin == InvoiceOrigin.MANUAL) {
            "Only outstanding balance and manual invoices can be deleted"
        }

        if (invoice.origin == InvoiceOrigin.OUTSTANDING_BALANCE && addToBalance) {
            val subscriber = requireNotNull(invoice.subscriber)
            val invoiceAmount = requireNotNull(invoice.totalAmount)
            subscriber.balance = subscriber.balance?.subtract(invoiceAmount) ?: invoiceAmount.negate()
            subscriberRepository.save(subscriber)
        }

        invoiceRepository.delete(invoice)

        return when {
            invoice.origin == InvoiceOrigin.MANUAL -> "Invoice deleted"
            addToBalance -> "Invoice deleted and balance restored to subscriber"
            else -> "Invoice deleted without balance adjustment"
        }
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
                predicates.add(root.get<InvoiceStatus>("status").`in`(InvoiceStatus.DRAFT, InvoiceStatus.SENT))
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
                notes = invoice.notes,
                origin = invoice.origin.name
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

    @Transactional(readOnly = true)
    fun buildSituationEmailInvoices(unpaidInvoices: List<UnpaidInvoiceDto>): List<WeeklySituationInvoiceDto> =
        unpaidInvoices.map { invoice ->
            WeeklySituationInvoiceDto(
                id = invoice.id,
                totalAmount = invoice.totalAmount,
                fromMonth = invoice.fromMonth,
                toMonth = invoice.toMonth,
                createdAt = invoice.createdAt,
                status = invoice.status,
                notes = invoice.notes?.trim()?.takeIf { it.isNotBlank() },
                origin = invoice.origin,
                ledgerEntries = ledgerEntryRepository.findByInvoiceId(invoice.id).map { entry ->
                    WeeklySituationLedgerEntryDto(
                        recordedMonth = entry.recordedMonth?.toString() ?: "",
                        subscriptionServiceName = entry.subscriptionService?.name ?: "",
                        notes = entry.notes,
                        amount = entry.amount ?: BigDecimal.ZERO
                    )
                }
            )
        }
}
