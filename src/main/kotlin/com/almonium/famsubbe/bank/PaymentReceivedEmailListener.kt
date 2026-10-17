package com.almonium.famsubbe.bank

import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.invoice.EmailFormat
import com.almonium.famsubbe.invoice.EmailInvoiceLine
import com.almonium.famsubbe.invoice.Invoice
import com.almonium.famsubbe.invoice.InvoiceEmailService
import com.almonium.famsubbe.invoice.InvoiceOrigin
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.invoice.PaymentReceivedEmail
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
import java.util.UUID

/** A bank transfer was applied to a subscriber; the payment-received email goes out once that commits. */
data class BankPaymentAppliedEvent(val bankTransactionId: UUID)

/**
 * Tells a subscriber their transfer arrived and what it paid. Runs after the
 * payment commits, so a failed or slow send never rolls back a payment, and a
 * transfer whose import lost the race to the other instance sends nothing.
 */
@Component
class PaymentReceivedEmailListener(
    private val transactionRepository: BankTransactionRepository,
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val standingService: SubscriberStandingService,
    private val paymentDetailsService: PaymentDetailsService,
    private val invoiceEmailService: InvoiceEmailService,
    transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(PaymentReceivedEmailListener::class.java)
    private val readOnly = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isReadOnly = true
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentApplied(event: BankPaymentAppliedEvent) {
        try {
            val email = readOnly.execute { build(event.bankTransactionId) } ?: return
            if (!invoiceEmailService.sendPaymentReceivedEmail(email)) {
                log.warn("Payment received email for bank transaction {} was not sent", event.bankTransactionId)
            }
        } catch (e: Exception) {
            log.error("Failed to send payment received email for bank transaction {}", event.bankTransactionId, e)
        }
    }

    private fun build(transactionId: UUID): PaymentReceivedEmail? {
        val transaction = transactionRepository.findById(transactionId).orElse(null) ?: return null
        val subscriber = transaction.subscriber ?: return null
        val toEmail = subscriber.email?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Family covered by the waiver has nothing to settle; a transfer from them is between people.
        if (subscriber.billingExempt) return null

        val amount = requireNotNull(transaction.amount)
        val settled = invoiceRepository.findByPaidByBankTransactionIdIn(listOf(transactionId))
            .sortedWith(compareBy<Invoice> { it.invoiceDate }.thenBy { it.createdAt })
        val open = standingService.unpaidInvoices(subscriber)
            .sortedWith(compareBy<Invoice> { it.invoiceDate }.thenBy { it.createdAt })
        val owed = SubscriberStandingService.amountOwed(open, subscriber)
        val settledSum = settled.fold(BigDecimal.ZERO) { sum, it -> sum + requireNotNull(it.totalAmount) }

        return PaymentReceivedEmail(
            toEmail = toEmail,
            name = subscriber.name.orEmpty(),
            amount = amount,
            arrivedAt = transaction.occurredAt ?: requireNotNull(transaction.createdAt),
            senderName = (transaction.senderName ?: transaction.counterName)?.trim()?.takeIf { it.isNotEmpty() },
            settled = settled.map { line(it, open = false) },
            stillOpen = open.map { line(it, open = true) },
            creditUsed = (settledSum - amount).max(BigDecimal.ZERO),
            balanceAfter = (subscriber.balance ?: BigDecimal.ZERO).max(BigDecimal.ZERO),
            toPay = owed.max(BigDecimal.ZERO),
            payment = paymentDetailsService.forSubscriber(subscriber, owed.max(BigDecimal.ZERO))
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
