package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.WeeklySituationEmailItemResult
import com.almonium.famsubbe.dto.WeeklySituationEmailResult
import com.almonium.famsubbe.repository.InvoiceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class WeeklySituationEmailService(
    private val invoiceRepository: InvoiceRepository,
    private val invoiceService: InvoiceService,
    private val invoiceEmailService: InvoiceEmailService,
    @Value($$"${app.email.dry-run:false}") private val isDryRun: Boolean
) {
    private val log = LoggerFactory.getLogger(WeeklySituationEmailService::class.java)

    @Scheduled(cron = "0 0 10 * * SAT", zone = "Europe/Kyiv")
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

    @Transactional(readOnly = true)
    fun sendWeeklySituationEmails(): WeeklySituationEmailResult {
        val subscriberIds = invoiceRepository.findSubscriberIdsWithUnpaidInvoices()
        val items = subscriberIds.map { subscriberId ->
            try {
                val details = invoiceService.getSubscriberDetails(subscriberId)
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

                val unpaidInvoices = invoiceService.buildSituationEmailInvoices(details.unpaidInvoices)

                val sent = invoiceEmailService.sendWeeklySituationEmail(
                    toEmail = email,
                    subscriberName = details.name,
                    totalOwed = details.totalAmountOwed,
                    subscriberBalance = details.balance,
                    activeSubscriptions = details.activeSubscriptions,
                    unpaidInvoices = unpaidInvoices
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
}
