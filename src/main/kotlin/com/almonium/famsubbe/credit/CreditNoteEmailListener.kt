package com.almonium.famsubbe.credit

import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.invoice.CreditNoteEmail
import com.almonium.famsubbe.invoice.EmailFormat
import com.almonium.famsubbe.invoice.EmailInvoiceLine
import com.almonium.famsubbe.invoice.Invoice
import com.almonium.famsubbe.invoice.InvoiceEmailService
import com.almonium.famsubbe.invoice.InvoiceOrigin
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.invoice.emailInvoiceTitle
import com.almonium.famsubbe.payment.PaymentDetailsService
import com.almonium.famsubbe.subscriber.SubscriberStandingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Tells a subscriber their credit note was recorded and what it paid. Runs
 * after it commits, so a failed or slow send never rolls back the credit, and
 * stamps [CreditNote.emailSentAt] in a short transaction of its own.
 */
@Component
class CreditNoteEmailListener(
    private val creditNoteRepository: CreditNoteRepository,
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val standingService: SubscriberStandingService,
    private val paymentDetailsService: PaymentDetailsService,
    private val invoiceEmailService: InvoiceEmailService,
    transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(CreditNoteEmailListener::class.java)
    private val readOnly = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isReadOnly = true
    }
    private val write = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onCreditNoteCreated(event: CreditNoteCreatedEvent) {
        try {
            val email = readOnly.execute { build(event.creditNoteId) } ?: return
            if (invoiceEmailService.sendCreditNoteEmail(email)) {
                write.executeWithoutResult {
                    creditNoteRepository.findById(event.creditNoteId).ifPresent { it.emailSentAt = Instant.now() }
                }
            } else {
                log.warn("Credit note email for {} was not sent", event.creditNoteId)
            }
        } catch (e: Exception) {
            log.error("Failed to send credit note email for {}", event.creditNoteId, e)
        }
    }

    private fun build(creditNoteId: UUID): CreditNoteEmail? {
        val note = creditNoteRepository.findById(creditNoteId).orElse(null) ?: return null
        if (note.undoneAt != null) return null
        val subscriber = requireNotNull(note.subscriber)
        val toEmail = subscriber.email?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val amount = requireNotNull(note.amount)
        val settled = invoiceRepository.findByPaidByCreditNoteId(creditNoteId)
            .sortedWith(compareBy<Invoice> { it.invoiceDate }.thenBy { it.createdAt })
        val open = standingService.unpaidInvoices(subscriber)
            .sortedWith(compareBy<Invoice> { it.invoiceDate }.thenBy { it.createdAt })
        val owed = SubscriberStandingService.amountOwed(open, subscriber).max(BigDecimal.ZERO)
        val settledSum = settled.fold(BigDecimal.ZERO) { sum, it -> sum + requireNotNull(it.totalAmount) }

        return CreditNoteEmail(
            toEmail = toEmail,
            name = subscriber.name.orEmpty(),
            amount = amount,
            occurredOn = requireNotNull(note.occurredOn),
            reason = note.reason.orEmpty(),
            settled = settled.map { line(it, open = false) },
            stillOpen = open.map { line(it, open = true) },
            creditUsed = (settledSum - amount).max(BigDecimal.ZERO),
            balanceAfter = (subscriber.balance ?: BigDecimal.ZERO).max(BigDecimal.ZERO),
            toPay = owed,
            payment = paymentDetailsService.forSubscriber(subscriber, owed)
        )
    }

    private fun line(invoice: Invoice, open: Boolean): EmailInvoiceLine {
        val what = if (invoice.origin == InvoiceOrigin.MANUAL) {
            "One-off"
        } else {
            ledgerEntryRepository.findByInvoiceId(requireNotNull(invoice.id))
                .mapNotNull { it.subscriptionService?.name }
                .distinct()
                .joinToString(", ")
        }
        val detail = if (open) "$what · sent ${EmailFormat.dayMonth(invoice.invoiceDate)}" else what
        return EmailInvoiceLine(emailInvoiceTitle(invoice), detail, requireNotNull(invoice.totalAmount))
    }
}
