package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.config.AppEmailProperties
import com.almonium.famsubbe.config.ZeptoMailProperties
import com.almonium.famsubbe.subscriber.ActiveSubscriptionDto
import com.almonium.famsubbe.reporting.WeeklySituationInvoiceDto
import com.almonium.famsubbe.accounting.LedgerEntry
import com.almonium.famsubbe.payment.PaymentDetails
import com.almonium.famsubbe.payment.PaymentDetailsService
import com.almonium.famsubbe.util.HtmlFileWriter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.thymeleaf.TemplateEngine
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class DefaultInvoiceEmailService(
    private val templateEngine: TemplateEngine,
    private val htmlFileWriter: HtmlFileWriter,
    private val zeptoProperties: ZeptoMailProperties,
    private val appEmailProperties: AppEmailProperties,
    private val paymentDetailsService: PaymentDetailsService,
    @Value($$"${app.web-domain}") private val webDomain: String
) : InvoiceEmailService {

    private val log = LoggerFactory.getLogger(DefaultInvoiceEmailService::class.java)
    private val restClient = RestClient.create()
    private val cabinetUrl = "$webDomain/subscriber/cabinet"

    override fun sendInvoiceEmail(invoice: Invoice, entries: List<LedgerEntry>, totalAmountOwed: BigDecimal): Boolean {
        val subscriber = invoice.subscriber ?: return false
        val toEmail = subscriber.email ?: return false

        return try {
            val name = subscriber.name.orEmpty()
            val total = requireNotNull(invoice.totalAmount)
            val isPaid = invoice.status == InvoiceStatus.PAID
            val isManual = invoice.origin == InvoiceOrigin.MANUAL
            val exempt = subscriber.billingExempt
            val credit = (subscriber.balance ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
            val services = EmailFormat.joinNatural(entries.mapNotNull { it.subscriptionService?.name }.distinct())
            val invoiceDate = EmailFormat.date(invoice.invoiceDate)

            val preheader = when {
                isPaid && exempt -> "Your share is covered. This one is just for your records."
                isPaid -> "Already paid from your credit. This one is just for your records."
                isManual && totalAmountOwed > total ->
                    "A one-off charge. Pay it together with the rest: ${EmailFormat.money(totalAmountOwed)} in total."
                isManual -> "A one-off charge. Pay in one tap from your banking app."
                else -> "$services for ${monthsText(invoice)}. Pay in one tap from your banking app."
            }
            val context = emailContext(buildInvoiceEmailSubject(invoice), preheader, webDomain)
            context.setVariable("invoice", invoice)
            context.setVariable("entries", entries)
            context.setVariable("isPaid", isPaid)
            context.setVariable("isManual", isManual)
            context.setVariable("period", formatInvoicePeriod(invoice.fromMonth, invoice.toMonth))
            context.setVariable(
                "lead", when {
                    isPaid && exempt ->
                        "Hi $name, there’s nothing to pay. Your share is covered, so this invoice was marked paid when it was made."
                    isPaid -> "Hi $name, there’s nothing to pay. Your credit already covered this invoice."
                    isManual -> "Hi $name, there’s a one-off charge for you."
                    else -> "Hi $name, here’s your share of $services for ${monthsText(invoice)}."
                }
            )
            context.setVariable(
                "dateLine", when {
                    isPaid && exempt -> "Paid $invoiceDate · your share is covered"
                    isPaid -> "Paid $invoiceDate from your credit"
                    else -> "Sent $invoiceDate"
                }
            )
            context.setVariable("totalAmountOwed", totalAmountOwed)
            context.setVariable("totalNote", totalNote(totalAmountOwed + credit - total, credit))
            context.setVariable("toPay", totalAmountOwed.max(BigDecimal.ZERO))
            context.setVariable("payment", paymentDetailsService.forSubscriber(subscriber, totalAmountOwed))
            context.setVariable("cabinetUrl", cabinetUrl)

            val htmlContent = templateEngine.process("invoice-email", context)
            sendEmail(toEmail, name, context.getVariable("subject") as String, htmlContent)
        } catch (e: Exception) {
            log.error("Failed to send invoice email to {}", toEmail, e)
            false
        }
    }

    override fun sendSituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>,
        payment: PaymentDetails
    ): Boolean = sendSituation(
        weekly = false,
        toEmail, subscriberName, totalOwed, subscriberBalance, activeSubscriptions, unpaidInvoices, payment
    )

    override fun sendWeeklySituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>,
        payment: PaymentDetails
    ): Boolean = sendSituation(
        weekly = true,
        toEmail, subscriberName, totalOwed, subscriberBalance, activeSubscriptions, unpaidInvoices, payment
    )

    private fun sendSituation(
        weekly: Boolean,
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>,
        payment: PaymentDetails
    ): Boolean {
        return try {
            val allClear = totalOwed <= BigDecimal.ZERO
            val toPay = totalOwed.max(BigDecimal.ZERO)
            val credit = subscriberBalance.max(BigDecimal.ZERO)
            val spareCredit = totalOwed.negate().max(BigDecimal.ZERO)
            val count = unpaidInvoices.size
            val invoicesText = "$count open ${if (count == 1) "invoice" else "invoices"}"

            val subject = when {
                weekly -> "Saturday check-in: ${EmailFormat.money(toPay)} still open"
                !allClear -> "Where you stand: ${EmailFormat.money(toPay)} to pay"
                spareCredit > BigDecimal.ZERO -> "All clear: nothing to pay, ${EmailFormat.money(spareCredit)} credit"
                else -> "All clear: nothing to pay"
            }
            val preheader = when {
                weekly -> "A gentle weekly nudge. One tap pays it all from your banking app."
                !allClear -> invoicesText + (if (credit > BigDecimal.ZERO) ", credit already applied" else "") +
                    ". Everything you need to pay is inside."
                spareCredit > BigDecimal.ZERO ->
                    "Nothing to pay. Your ${EmailFormat.money(spareCredit)} credit goes toward the next invoice."
                else -> "Nothing to pay right now."
            }
            val footerNote = if (weekly) {
                "You get this on Saturdays while something is open."
            } else {
                "You got this because an update on your account was requested."
            }

            val context = emailContext(subject, preheader, webDomain, footerNote)
            context.setVariable("name", subscriberName)
            context.setVariable("weekly", weekly)
            context.setVariable("allClear", allClear)
            context.setVariable("toPay", toPay)
            context.setVariable("credit", credit)
            context.setVariable("spareCredit", spareCredit)
            context.setVariable("unpaidSum", unpaidInvoices.fold(BigDecimal.ZERO) { sum, it -> sum + it.totalAmount })
            context.setVariable("unpaidInvoices", unpaidInvoices)
            context.setVariable("unpaidInvoicesCount", count)
            context.setVariable("activeSubscriptions", activeSubscriptions)
            context.setVariable("payment", payment)
            context.setVariable("cabinetUrl", cabinetUrl)

            val htmlContent = templateEngine.process("situation-email", context)
            sendEmail(toEmail, subscriberName, subject, htmlContent)
        } catch (e: Exception) {
            log.error("Failed to send {} situation email to {}", if (weekly) "weekly" else "on-demand", toEmail, e)
            false
        }
    }

    override fun sendDebtPaidEmail(
        toEmail: String,
        subscriberName: String,
        paidInvoicesCount: Int,
        totalPaidAmount: BigDecimal,
        balanceAfter: BigDecimal,
        creditWrittenOff: BigDecimal
    ): Boolean {
        return try {
            val subject = if (paidInvoicesCount == 1) {
                "Thanks, $subscriberName: your invoice is paid"
            } else {
                "Thanks, $subscriberName: all $paidInvoicesCount invoices are paid"
            }
            val settled = EmailFormat.money(totalPaidAmount)
            val preheader = if (paidInvoicesCount == 1) {
                "$settled settled. Nothing left to pay."
            } else {
                "$settled settled across $paidInvoicesCount invoices. Nothing left to pay."
            }

            val context = emailContext(subject, preheader, webDomain)
            context.setVariable("name", subscriberName)
            context.setVariable("paidInvoicesCount", paidInvoicesCount)
            context.setVariable("totalPaidAmount", totalPaidAmount)
            context.setVariable("balanceAfter", balanceAfter)
            context.setVariable("creditWrittenOff", creditWrittenOff)
            context.setVariable("cabinetUrl", cabinetUrl)

            val htmlContent = templateEngine.process("debt-paid-email", context)
            sendEmail(toEmail, subscriberName, subject, htmlContent)
        } catch (e: Exception) {
            log.error("Failed to send debt paid email to {}", toEmail, e)
            false
        }
    }

    private fun sendEmail(toEmail: String, toName: String?, subject: String, htmlContent: String): Boolean {
        if (appEmailProperties.dryRun) {
            log.info("Email sending is disabled. Skipping sending email to {}", toEmail)
            htmlFileWriter.saveHtmlToFile(EmailLogo.embedForPreview(htmlContent))
            return true
        }

        val requestBody = mapOf(
            "from" to mapOf(
                "address" to zeptoProperties.fromAddress,
                "name" to zeptoProperties.fromName
            ),
            "to" to listOf(
                mapOf(
                    "email_address" to mapOf(
                        "address" to toEmail,
                        "name" to (toName ?: toEmail)
                    )
                )
            ),
            "subject" to subject,
            "reply_to" to replyTo(appEmailProperties.replyTo),
            "htmlbody" to htmlContent,
            "inline_images" to EmailLogo.inlineImages()
        )

        return try {
            val response = restClient.post()
                .uri(zeptoProperties.apiUrl)
                .header("Authorization", "Zoho-enczapikey ${zeptoProperties.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity()

            val success = response.statusCode.is2xxSuccessful
            if (success) {
                log.info("Successfully sent email to {}", toEmail)
            } else {
                log.error("Failed to send email to {}. Status code: {}", toEmail, response.statusCode)
            }
            success
        } catch (e: Exception) {
            log.error("Error calling ZeptoMail API for {}", toEmail, e)
            false
        }
    }

    /** "Includes 186,00 ₴ from other open invoices, less 40,00 ₴ credit", or nothing when the total is just this invoice. */
    private fun totalNote(otherOpen: BigDecimal, credit: BigDecimal): String? {
        val parts = listOfNotNull(
            otherOpen.takeIf { it > BigDecimal.ZERO }?.let { "includes ${EmailFormat.money(it)} from other open invoices" },
            credit.takeIf { it > BigDecimal.ZERO }?.let { "less ${EmailFormat.money(it)} credit" }
        )
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { null }
    }
}

internal fun buildInvoiceEmailSubject(invoice: Invoice): String {
    val amount = EmailFormat.money(invoice.totalAmount)
    val period = formatInvoicePeriod(invoice.fromMonth, invoice.toMonth)
    val notes = invoice.notes
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    return when {
        invoice.status == InvoiceStatus.PAID && invoice.origin == InvoiceOrigin.MANUAL ->
            "Receipt for ${notes ?: "a one-off charge"}: nothing to pay"
        invoice.status == InvoiceStatus.PAID -> "Receipt for $period: nothing to pay"
        invoice.origin == InvoiceOrigin.MANUAL -> "${notes ?: "One-off charge"}: $amount"
        else -> "Your $period share: $amount"
    }
}

/** September · September and October · July to September, for the invoice's first line. */
private fun monthsText(invoice: Invoice): String {
    val from = requireNotNull(invoice.fromMonth)
    val to = requireNotNull(invoice.toMonth)
    val month = DateTimeFormatter.ofPattern(if (from.year == to.year) "MMMM" else "MMMM yyyy", Locale.ENGLISH)
    return when (to) {
        from -> from.format(month)
        from.plusMonths(1) -> "${from.format(month)} and ${to.format(month)}"
        else -> "${from.format(month)} to ${to.format(month)}"
    }
}

internal fun formatInvoicePeriod(fromMonth: YearMonth?, toMonth: YearMonth?): String {
    val from = requireNotNull(fromMonth)
    val to = requireNotNull(toMonth)
    val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

    if (from == to) {
        return from.format(monthFormatter)
    }

    return if (from.year == to.year) {
        val shortMonthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
        "${from.format(shortMonthFormatter)}–${to.format(monthFormatter)}"
    } else {
        "${from.format(monthFormatter)}–${to.format(monthFormatter)}"
    }
}
