package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.subscriber.SubscriberStandingService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.*

/**
 * Emails invoices and moves them from DRAFT to SENT once the mail has really
 * gone out.
 *
 * Invoices are created and committed first, by [InvoiceService]. Each email
 * then gets its own short transaction, so a failure on one invoice cannot roll
 * back invoices that were already created, or the SENT status of mail that was
 * already delivered. That rollback is what used to make a retry send the same
 * email twice.
 */
@Service
class InvoiceDeliveryService(
    private val invoiceService: InvoiceService,
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val standingService: SubscriberStandingService,
    private val invoiceEmailService: InvoiceEmailService,
    private val transactionTemplate: TransactionTemplate,
    @Value($$"${app.email.dry-run:false}") private val isDryRun: Boolean
) {
    private val log = LoggerFactory.getLogger(InvoiceDeliveryService::class.java)

    fun generate(request: InvoiceGenerationRequest, performedByAccountId: UUID): InvoiceGenerationResult {
        val result = invoiceService.generateInvoices(request, performedByAccountId)

        return result.copy(items = result.items.map { item ->
            if (!item.emailRequested) {
                return@map item
            }
            val delivery = deliverQuietly(item.invoiceId)
            item.copy(
                emailSent = delivery?.markedSent == true,
                message = if (delivery?.sent == true) {
                    item.message
                } else {
                    "Created invoice with ${item.ledgerEntryCount} ledger entries, but the email could not be sent"
                }
            )
        })
    }

    fun createManual(request: ManualInvoiceCreateRequest, performedByAccountId: UUID): InvoiceResponse =
        deliverIfRequested(invoiceService.createManualInvoice(request, performedByAccountId), request.sendEmail)

    fun duplicate(invoiceId: UUID, request: InvoiceDuplicateRequest, performedByAccountId: UUID): InvoiceResponse =
        deliverIfRequested(invoiceService.duplicateInvoice(invoiceId, request, performedByAccountId), request.sendEmail)

    /** Emails one invoice. True when the mail went out (or would have, in dry-run). */
    fun send(invoiceId: UUID): Boolean = deliver(invoiceId).sent

    fun sendDrafts(): InvoiceBulkEmailResult {
        val drafts = requireNotNull(transactionTemplate.execute {
            // Drafts, plus receipts for invoices their credit paid when they were generated.
            (
                invoiceRepository.findByStatusInWithSubscriber(listOf(InvoiceStatus.DRAFT)) +
                    invoiceRepository.findUnsentReceipts(InvoiceService.PAID_FROM_CREDIT_NOTE)
            ).map { invoice ->
                val subscriber = requireNotNull(invoice.subscriber)
                Triple(requireNotNull(invoice.id), requireNotNull(subscriber.id), requireNotNull(subscriber.name))
            }
        })

        val items = drafts.map { (invoiceId, subscriberId, subscriberName) ->
            try {
                val delivery = deliver(invoiceId)
                InvoiceBulkEmailItemResult(
                    invoiceId = invoiceId,
                    subscriberId = subscriberId,
                    subscriberName = subscriberName,
                    statusBefore = delivery.statusBefore.name,
                    statusAfter = delivery.statusAfter.name,
                    sent = delivery.sent,
                    updated = delivery.markedSent,
                    message = if (delivery.sent) "Invoice email sent successfully" else "Failed to send invoice email"
                )
            } catch (e: Exception) {
                log.error("Failed to deliver invoice {}", invoiceId, e)
                InvoiceBulkEmailItemResult(
                    invoiceId = invoiceId,
                    subscriberId = subscriberId,
                    subscriberName = subscriberName,
                    statusBefore = InvoiceStatus.DRAFT.name,
                    statusAfter = InvoiceStatus.DRAFT.name,
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

    /** A new invoice created already PAID (billing-exempt) has nothing to ask for, so it is never emailed. */
    private fun deliverIfRequested(invoice: InvoiceResponse, sendEmail: Boolean): InvoiceResponse {
        if (!sendEmail || invoice.status == InvoiceStatus.PAID.name) {
            return invoice
        }
        deliverQuietly(invoice.id)
        return invoiceService.getInvoice(invoice.id).invoice
    }

    private fun deliverQuietly(invoiceId: UUID): Delivery? =
        try {
            deliver(invoiceId)
        } catch (e: Exception) {
            log.error("Failed to deliver invoice {}", invoiceId, e)
            null
        }

    private fun deliver(invoiceId: UUID): Delivery = requireNotNull(transactionTemplate.execute {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }
        check(invoice.status != InvoiceStatus.VOID) { "Voided invoices cannot be emailed" }

        val statusBefore = invoice.status
        val sent = invoiceEmailService.sendInvoiceEmail(
            invoice,
            ledgerEntryRepository.findByInvoiceId(invoiceId),
            standingService.amountOwed(requireNotNull(invoice.subscriber))
        )

        // A dry-run send reports success but must not move the invoice out of DRAFT.
        val markedSent = sent && !isDryRun && invoice.status == InvoiceStatus.DRAFT
        if (markedSent) {
            invoice.markSent()
            invoiceRepository.save(invoice)
        } else if (sent && !isDryRun && invoice.status == InvoiceStatus.PAID) {
            // A receipt: record that it went out, so it is not sent again, without leaving PAID.
            invoice.emailSent = true
            invoice.sentAt = Instant.now()
            invoiceRepository.save(invoice)
        }

        Delivery(statusBefore, invoice.status, sent, markedSent)
    })

    private data class Delivery(
        val statusBefore: InvoiceStatus,
        val statusAfter: InvoiceStatus,
        val sent: Boolean,
        val markedSent: Boolean
    )
}
