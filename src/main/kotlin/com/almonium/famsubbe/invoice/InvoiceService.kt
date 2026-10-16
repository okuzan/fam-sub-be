package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.subscriber.SubscriberDebtPaymentItemResult
import com.almonium.famsubbe.subscriber.SubscriberDebtPaymentResult
import com.almonium.famsubbe.accounting.LedgerEntry
import com.almonium.famsubbe.util.BillingTime
import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val subscriberRepository: SubscriberRepository
) {
    private companion object {
        const val BILLING_EXEMPT_NOTE = "Settled automatically: subscriber is billing-exempt"
    }

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
        val invoiceDate = BillingTime.dateOf(now)
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

            val exempt = subscriber.billingExempt
            val invoice = invoiceRepository.save(
                Invoice().apply {
                    this.subscriber = subscriber
                    this.fromMonth = request.fromMonth
                    this.toMonth = request.toMonth
                    this.invoiceDate = invoiceDate
                    this.totalAmount = invoiceTotal
                    this.status = if (exempt) InvoiceStatus.PAID else InvoiceStatus.DRAFT
                    this.createdAt = now
                    this.statusChangedAt = now
                    this.createdByAccountId = performedByAccountId
                    this.notes = BILLING_EXEMPT_NOTE.takeIf { exempt }
                    this.origin = InvoiceOrigin.SUBSCRIPTION_LEDGER
                    this.invoiceGenerationRun = generationRun
                }
            )

            subscriberEntries.forEach { it.invoice = invoice }
            ledgerEntryRepository.saveAll(subscriberEntries)

            // Emails go out after this transaction commits, from InvoiceDeliveryService,
            // which moves the invoice to SENT only once the mail really went out.
            val emailRequested = request.sendEmail && !exempt

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
                emailRequested = emailRequested,
                emailSent = false,
                message = if (exempt) {
                    "Created settled invoice for billing-exempt subscriber with ${subscriberEntries.size} ledger entries"
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

        return InvoiceSuggestion(
            lastInvoicedToMonth = lastInvoice?.toMonth,
            suggestedFromMonth = ledgerEntryRepository.findOldestUninvoicedMonth(),
            suggestedToMonth = ledgerEntryRepository.findLatestUninvoicedMonth()
        )
    }

    private fun Invoice.toResponse(): InvoiceResponse =
        InvoiceResponse(
            id = requireNotNull(id),
            subscriberId = requireNotNull(subscriber?.id),
            subscriberName = requireNotNull(subscriber?.name),
            fromMonth = requireNotNull(fromMonth),
            toMonth = requireNotNull(toMonth),
            invoiceDate = requireNotNull(invoiceDate),
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

    private fun initialInvoiceStatus(subscriber: Subscriber): InvoiceStatus =
        if (subscriber.billingExempt) InvoiceStatus.PAID else InvoiceStatus.DRAFT

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

    fun updateStatus(invoiceId: UUID, status: InvoiceStatus): InvoiceStatusChange {
        // Voiding has to release the ledger entries, so it never goes through a
        // bare status write.
        if (status == InvoiceStatus.VOID) {
            return InvoiceStatusChange(voidInvoice(invoiceId, null), balanceRefunded = null)
        }

        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        // A voided invoice has already given its ledger entries back to the pool,
        // so reopening it would bill the same cost twice.
        check(invoice.status != InvoiceStatus.VOID) { "Voided invoices cannot be reopened" }

        // Reopening an invoice that was paid from balance gives that money back,
        // otherwise the subscriber's credit is simply lost.
        val refund = invoice.balancePaidAmount?.takeIf {
            invoice.status == InvoiceStatus.PAID && status != InvoiceStatus.PAID
        }
        if (refund != null) {
            val subscriber = requireNotNull(invoice.subscriber)
            subscriber.balance = (subscriber.balance ?: BigDecimal.ZERO).add(refund)
            subscriberRepository.save(subscriber)
            invoice.balancePaidAmount = null
        }

        invoice.transitionTo(status)
        val updatedInvoice = invoiceRepository.save(invoice)

        return InvoiceStatusChange(updatedInvoice.toResponse(), balanceRefunded = refund)
    }

    fun createManualInvoice(
        request: ManualInvoiceCreateRequest,
        performedByAccountId: UUID
    ): InvoiceResponse {
        val subscriber = subscriberRepository.findById(request.subscriberId)
            .orElseThrow { IllegalArgumentException("Subscriber not found: ${request.subscriberId}") }

        val now = Instant.now()
        val invoiceMonth = YearMonth.from(request.invoiceDate)
        val invoice = invoiceRepository.save(
            Invoice().apply {
                val exempt = subscriber.billingExempt
                this.subscriber = subscriber
                this.fromMonth = invoiceMonth
                this.toMonth = invoiceMonth
                this.invoiceDate = request.invoiceDate
                this.totalAmount = request.amount
                this.status = initialInvoiceStatus(subscriber)
                this.createdAt = now
                this.statusChangedAt = now
                this.createdByAccountId = performedByAccountId
                this.notes = listOfNotNull(
                    request.notes.trim().takeIf { it.isNotBlank() },
                    BILLING_EXEMPT_NOTE.takeIf { exempt }
                ).joinToString("; ").takeIf { it.isNotBlank() }
                this.origin = InvoiceOrigin.MANUAL
            }
        )

        return invoice.toResponse()
    }

    fun duplicateInvoice(
        invoiceId: UUID,
        request: InvoiceDuplicateRequest,
        performedByAccountId: UUID
    ): InvoiceResponse {
        val source = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }
        val subscriber = subscriberRepository.findById(request.subscriberId)
            .orElseThrow { IllegalArgumentException("Subscriber not found: ${request.subscriberId}") }

        val now = Instant.now()
        val invoiceDate = request.invoiceDate ?: BillingTime.dateOf(now)
        val invoiceMonth = YearMonth.from(invoiceDate)
        val invoice = invoiceRepository.save(
            Invoice().apply {
                this.subscriber = subscriber
                this.fromMonth = invoiceMonth
                this.toMonth = invoiceMonth
                this.invoiceDate = invoiceDate
                this.totalAmount = request.amount ?: requireNotNull(source.totalAmount)
                this.status = initialInvoiceStatus(subscriber)
                this.createdAt = now
                this.statusChangedAt = now
                this.createdByAccountId = performedByAccountId
                this.notes = if (request.notes == null) {
                    source.notes
                } else {
                    request.notes.trim().takeIf { it.isNotBlank() }
                }
                this.origin = InvoiceOrigin.MANUAL
            }
        )

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
            invoice.transitionTo(InvoiceStatus.PAID)
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

    /**
     * Money that reached us from a subscriber, such as a bank transfer. It is
     * credited to their balance, then settles unpaid invoices from it: first an
     * invoice of exactly the paid amount, since that is plainly what the payer
     * meant, then the rest oldest first, stopping at the first one the balance
     * cannot cover so newer invoices never jump older debt. Whatever is left
     * stays as credit.
     */
    fun applyIncomingPayment(subscriber: Subscriber, amount: BigDecimal): IncomingPaymentResult {
        require(amount > BigDecimal.ZERO) { "Payment amount must be positive" }

        subscriber.balance = (subscriber.balance ?: BigDecimal.ZERO).add(amount)
        subscriberRepository.save(subscriber)

        val unpaid = invoiceRepository.findBySubscriberAndStatusIn(
            subscriber,
            listOf(InvoiceStatus.DRAFT, InvoiceStatus.SENT)
        ).sortedWith(compareBy<Invoice> { it.invoiceDate }.thenBy { it.createdAt })
        val exactMatch = unpaid.firstOrNull { it.totalAmount?.compareTo(amount) == 0 }
        val settlementOrder = listOfNotNull(exactMatch) + unpaid.filter { it !== exactMatch }

        val settled = mutableListOf<Invoice>()
        for (invoice in settlementOrder) {
            if ((subscriber.balance ?: BigDecimal.ZERO) < requireNotNull(invoice.totalAmount)) break
            settled += payInvoiceFromBalance(invoice)
        }

        return IncomingPaymentResult(
            settledInvoiceIds = settled.map { requireNotNull(it.id) },
            settledAmount = settled.map { requireNotNull(it.totalAmount) }.fold(BigDecimal.ZERO, BigDecimal::add),
            balanceAfter = subscriber.balance ?: BigDecimal.ZERO
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

        invoice.balancePaidAmount = invoiceAmount
        invoice.transitionTo(InvoiceStatus.PAID)

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

            filter.invoiceDateFrom?.let {
                predicates += cb.greaterThanOrEqualTo(root.get("invoiceDate"), it)
            }

            filter.invoiceDateTo?.let {
                predicates += cb.lessThanOrEqualTo(root.get("invoiceDate"), it)
            }

            origin?.let {
                predicates += cb.equal(root.get<InvoiceOrigin>("origin"), it)
            }

            cb.and(*predicates.toTypedArray())
        }

        return invoiceRepository.findAll(
            spec,
            Sort.by(
                Sort.Order.desc("invoiceDate"),
                Sort.Order.desc("createdAt")
            )
        )
            .map { it.toResponse() }
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
        invoice.transitionTo(InvoiceStatus.VOID)

        // Release the ledger entries back into the uninvoiced pool. Without this a
        // voided invoice keeps its entries forever and the cost silently
        // disappears: generation only ever picks up entries with no invoice.
        val releasedEntries = ledgerEntryRepository.findByInvoice(invoice)
        if (releasedEntries.isNotEmpty()) {
            releasedEntries.forEach { it.invoice = null }
            ledgerEntryRepository.saveAll(releasedEntries)
        }

        return invoiceRepository.save(invoice).toResponse()
    }

    fun deleteInvoice(invoiceId: UUID): String {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        check(invoice.status == InvoiceStatus.DRAFT) {
            "Only DRAFT invoices can be deleted"
        }

        check(invoice.origin == InvoiceOrigin.MANUAL) {
            "Only manual invoices can be deleted"
        }

        invoiceRepository.delete(invoice)

        return "Invoice deleted"
    }
}
