package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.subscriber.SubscriberStandingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate

/**
 * Tells a subscriber not to pay an invoice they were emailed and that has
 * since been voided. Drafts are voided silently: nobody has seen them.
 */
@Component
class InvoiceCancelledEmailListener(
    private val invoiceRepository: InvoiceRepository,
    private val standingService: SubscriberStandingService,
    private val invoiceEmailService: InvoiceEmailService,
    transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(InvoiceCancelledEmailListener::class.java)

    // Like invoice delivery: one short transaction per email, after the void has committed.
    private val readOnly = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isReadOnly = true
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSentInvoiceVoided(event: SentInvoiceVoidedEvent) {
        try {
            val sent = readOnly.execute {
                val invoice = invoiceRepository.findById(event.invoiceId).orElse(null) ?: return@execute true
                val subscriber = requireNotNull(invoice.subscriber)
                invoiceEmailService.sendInvoiceCancelledEmail(invoice, standingService.amountOwed(subscriber))
            }
            if (sent != true) log.warn("Invoice cancelled email for invoice {} was not sent", event.invoiceId)
        } catch (e: Exception) {
            log.error("Failed to send invoice cancelled email for invoice {}", event.invoiceId, e)
        }
    }
}
