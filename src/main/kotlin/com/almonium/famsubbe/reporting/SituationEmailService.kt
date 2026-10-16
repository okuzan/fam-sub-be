package com.almonium.famsubbe.reporting

import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.invoice.InvoiceEmailService
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.payment.PaymentDetailsService
import com.almonium.famsubbe.subscriber.SubscriberDetailResponse
import com.almonium.famsubbe.subscriber.SubscriberRepository
import com.almonium.famsubbe.subscriber.SubscriberStandingService
import com.almonium.famsubbe.subscriber.UnpaidInvoiceDto
import com.almonium.famsubbe.util.BillingTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.*

/**
 * The "where you stand" email: sent on demand from the admin subscriber view,
 * and every Saturday to everyone who owes something.
 *
 * Deliberately not transactional: each subscriber is read in its own
 * transaction, so one failure cannot mark a shared one rollback-only, and no
 * connection is held open across the mail API calls.
 */
@Service
class SituationEmailService(
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val standingService: SubscriberStandingService,
    private val invoiceEmailService: InvoiceEmailService,
    private val subscriberRepository: SubscriberRepository,
    private val paymentDetailsService: PaymentDetailsService,
    @Value($$"${app.email.dry-run:false}") private val isDryRun: Boolean
) {
    private val log = LoggerFactory.getLogger(SituationEmailService::class.java)

    /** Returns the details the email was built from, for the audit record. */
    fun sendOnDemand(subscriberId: UUID): SubscriberDetailResponse {
        val details = standingService.getDetails(subscriberId)
        require(details.email.isNotBlank()) { "Subscriber has no email address" }

        val sent = invoiceEmailService.sendSituationEmail(
            toEmail = details.email,
            subscriberName = details.name,
            totalOwed = details.totalAmountOwed,
            subscriberBalance = details.balance,
            activeSubscriptions = details.activeSubscriptions,
            unpaidInvoices = toEmailInvoices(details.unpaidInvoices),
            payment = paymentFor(details)
        )
        if (!sent) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send email")
        }
        return details
    }

    @Scheduled(cron = "0 0 10 * * SAT", zone = BillingTime.ZONE_ID)
    fun sendWeeklySituationEmailsOnSaturday() {
        val result = sendWeeklySituationEmails()
        log.info(
            "Weekly situation email job finished: attempted={}, sent={}, failed={}, skipped={}, dryRun={}",
            result.attemptedCount,
            result.sentCount,
            result.failedCount,
            result.skippedCount,
            result.dryRun
        )
    }

    fun sendWeeklySituationEmails(): WeeklySituationEmailResult {
        val subscriberIds = invoiceRepository.findSubscriberIdsWithUnpaidInvoices()
        val items = subscriberIds.map { subscriberId ->
            try {
                val details = standingService.getDetails(subscriberId)
                val email = details.email.trim().takeIf { it.isNotEmpty() }

                if (details.totalAmountOwed <= BigDecimal.ZERO) {
                    return@map WeeklySituationEmailItemResult(
                        subscriberId = subscriberId,
                        subscriberName = details.name,
                        email = email,
                        totalOwed = details.totalAmountOwed,
                        sent = false,
                        skipped = true,
                        message = "Skipped because subscriber does not currently owe anything"
                    )
                }

                if (email == null) {
                    return@map WeeklySituationEmailItemResult(
                        subscriberId = subscriberId,
                        subscriberName = details.name,
                        email = null,
                        totalOwed = details.totalAmountOwed,
                        sent = false,
                        skipped = true,
                        message = "Skipped because subscriber has no email address"
                    )
                }

                val sent = invoiceEmailService.sendWeeklySituationEmail(
                    toEmail = email,
                    subscriberName = details.name,
                    totalOwed = details.totalAmountOwed,
                    subscriberBalance = details.balance,
                    activeSubscriptions = details.activeSubscriptions,
                    unpaidInvoices = toEmailInvoices(details.unpaidInvoices),
                    payment = paymentFor(details)
                )

                WeeklySituationEmailItemResult(
                    subscriberId = subscriberId,
                    subscriberName = details.name,
                    email = email,
                    totalOwed = details.totalAmountOwed,
                    sent = sent,
                    skipped = false,
                    message = if (sent) {
                        "Weekly situation email sent successfully"
                    } else {
                        "Failed to send weekly situation email"
                    }
                )
            } catch (e: Exception) {
                log.error("Failed to process weekly situation email for subscriber {}", subscriberId, e)
                WeeklySituationEmailItemResult(
                    subscriberId = subscriberId,
                    subscriberName = "",
                    email = null,
                    totalOwed = BigDecimal.ZERO,
                    sent = false,
                    skipped = false,
                    message = e.message ?: "Failed to process weekly situation email"
                )
            }
        }

        return WeeklySituationEmailResult(
            attemptedCount = items.count { !it.skipped },
            sentCount = items.count { it.sent },
            failedCount = items.count { !it.sent && !it.skipped },
            skippedCount = items.count { it.skipped },
            dryRun = isDryRun,
            items = items
        )
    }

    private fun paymentFor(details: SubscriberDetailResponse) =
        paymentDetailsService.forSubscriber(
            subscriberRepository.findById(details.id).orElseThrow(),
            details.totalAmountOwed
        )

    private fun toEmailInvoices(unpaidInvoices: List<UnpaidInvoiceDto>): List<WeeklySituationInvoiceDto> =
        unpaidInvoices.map { invoice ->
            WeeklySituationInvoiceDto(
                id = invoice.id,
                totalAmount = invoice.totalAmount,
                fromMonth = invoice.fromMonth,
                toMonth = invoice.toMonth,
                invoiceDate = invoice.invoiceDate,
                createdAt = invoice.createdAt,
                status = invoice.status,
                notes = invoice.notes?.trim()?.takeIf { it.isNotBlank() },
                origin = invoice.origin,
                ledgerEntries = ledgerEntryRepository.findByInvoiceId(invoice.id).map { entry ->
                    WeeklySituationLedgerEntryDto(
                        recordedMonth = entry.recordedMonth?.toString().orEmpty(),
                        subscriptionServiceName = entry.subscriptionService?.name.orEmpty(),
                        notes = entry.notes,
                        amount = entry.amount ?: BigDecimal.ZERO
                    )
                }
            )
        }
}
