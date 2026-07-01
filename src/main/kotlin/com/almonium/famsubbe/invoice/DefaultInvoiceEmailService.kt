package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.config.AppEmailProperties
import com.almonium.famsubbe.config.PaymentProperties
import com.almonium.famsubbe.config.ZeptoMailProperties
import com.almonium.famsubbe.subscriber.ActiveSubscriptionDto
import com.almonium.famsubbe.reporting.WeeklySituationInvoiceDto
import com.almonium.famsubbe.accounting.LedgerEntry
import com.almonium.famsubbe.util.HtmlFileWriter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
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
    private val paymentProperties: PaymentProperties,
    @Value($$"${app.web-domain}") private val webDomain: String
) : InvoiceEmailService {

    private val log = LoggerFactory.getLogger(DefaultInvoiceEmailService::class.java)
    private val restClient = RestClient.create()

    override fun sendInvoiceEmail(invoice: Invoice, entries: List<LedgerEntry>, totalAmountOwed: BigDecimal): Boolean {
        val subscriber = invoice.subscriber ?: return false
        val toEmail = subscriber.email ?: return false

        return try {
            val context = Context()
            context.setVariable("invoice", invoice)
            context.setVariable("subscriber", subscriber)
            context.setVariable("entries", entries)
            context.setVariable("origin", invoice.origin)
            context.setVariable("totalAmountOwed", totalAmountOwed)
            context.setVariable("paymentMethods", paymentProperties.methods)
            context.setVariable("cabinetUrl", "$webDomain/subscriber/cabinet")
            context.setVariable("invoicePeriod", formatInvoicePeriod(invoice.fromMonth, invoice.toMonth))

            val htmlContent = templateEngine.process("invoice-email", context)
            val subject = buildInvoiceEmailSubject(invoice)

            sendEmail(toEmail, subscriber.name, subject, htmlContent)
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
        unpaidInvoices: List<WeeklySituationInvoiceDto>
    ): Boolean {
        return try {
            val context = Context()
            context.setVariable("name", subscriberName)
            context.setVariable("totalOwed", totalOwed)
            context.setVariable("subscriberBalance", subscriberBalance)
            context.setVariable("activeSubscriptions", activeSubscriptions)
            context.setVariable("unpaidInvoices", unpaidInvoices)
            context.setVariable("unpaidInvoicesCount", unpaidInvoices.size)
            context.setVariable("activeSubscriptionsCount", activeSubscriptions.size)
            context.setVariable("paymentMethods", paymentProperties.methods)

            val htmlContent = templateEngine.process("situation-email", context)
            val subject = "Your Subscription Account Status"

            sendEmail(toEmail, subscriberName, subject, htmlContent)
        } catch (e: Exception) {
            log.error("Failed to send situation email to {}", toEmail, e)
            false
        }
    }

    override fun sendWeeklySituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>
    ): Boolean {
        return try {
            val context = Context()
            context.setVariable("name", subscriberName)
            context.setVariable("totalOwed", totalOwed)
            context.setVariable("subscriberBalance", subscriberBalance)
            context.setVariable("activeSubscriptions", activeSubscriptions)
            context.setVariable("unpaidInvoices", unpaidInvoices)
            context.setVariable("unpaidInvoicesCount", unpaidInvoices.size)
            context.setVariable("activeSubscriptionsCount", activeSubscriptions.size)
            context.setVariable("paymentMethods", paymentProperties.methods)

            val htmlContent = templateEngine.process("weekly-situation-email", context)
            val subject = "Weekly Subscription Account Check-up"

            sendEmail(toEmail, subscriberName, subject, htmlContent)
        } catch (e: Exception) {
            log.error("Failed to send weekly situation email to {}", toEmail, e)
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
            val context = Context()
            context.setVariable("name", subscriberName)
            context.setVariable("paidInvoicesCount", paidInvoicesCount)
            context.setVariable("totalPaidAmount", totalPaidAmount)
            context.setVariable("balanceAfter", balanceAfter)
            context.setVariable("creditWrittenOff", creditWrittenOff)
            context.setVariable("cabinetUrl", "$webDomain/subscriber/cabinet")

            val htmlContent = templateEngine.process("debt-paid-email", context)
            val subject = "Your Debt Has Been Paid"

            sendEmail(toEmail, subscriberName, subject, htmlContent)
        } catch (e: Exception) {
            log.error("Failed to send debt paid email to {}", toEmail, e)
            false
        }
    }

    private fun sendEmail(toEmail: String, toName: String?, subject: String, htmlContent: String): Boolean {
        if (appEmailProperties.dryRun) {
            log.info("Email sending is disabled. Skipping sending email to {}", toEmail)
            htmlFileWriter.saveHtmlToFile(htmlContent)
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
            "htmlbody" to htmlContent
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
}

internal fun buildInvoiceEmailSubject(invoice: Invoice): String {
    val amount = requireNotNull(invoice.totalAmount).setScale(2).toPlainString()
    val detail = when (invoice.origin) {
        InvoiceOrigin.MANUAL -> invoice.notes
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "Manual charge"
        InvoiceOrigin.SUBSCRIPTION_LEDGER ->
            "Subscriptions, ${formatInvoicePeriod(invoice.fromMonth, invoice.toMonth)}"
    }

    return "New Invoice: ₴$amount — $detail"
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
