package com.almonium.famsubbe.service

import com.almonium.famsubbe.config.AppEmailProperties
import com.almonium.famsubbe.config.ZeptoMailProperties
import com.almonium.famsubbe.entity.Invoice
import com.almonium.famsubbe.entity.LedgerEntry
import com.almonium.famsubbe.util.HtmlFileWriter
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Service
class DefaultInvoiceEmailService(
    private val templateEngine: TemplateEngine,
    private val htmlFileWriter: HtmlFileWriter,
    private val zeptoProperties: ZeptoMailProperties,
    private val appEmailProperties: AppEmailProperties
) : InvoiceEmailService {

    private val log = LoggerFactory.getLogger(DefaultInvoiceEmailService::class.java)
    private val restClient = RestClient.create()

    override fun sendInvoiceEmail(invoice: Invoice, entries: List<LedgerEntry>): Boolean {
        val subscriber = invoice.subscriber ?: return false
        val toEmail = subscriber.email ?: return false

        return try {
            val context = Context()
            context.setVariable("invoice", invoice)
            context.setVariable("subscriber", subscriber)
            context.setVariable("entries", entries)
            context.setVariable("origin", invoice.origin)

            val htmlContent = templateEngine.process("invoice-email", context)
            val subject = "Your Invoice for ${invoice.fromMonth} - ${invoice.toMonth}"

            sendEmail(toEmail, subscriber.name, subject, htmlContent)
        } catch (e: Exception) {
            log.error("Failed to send invoice email to {}", toEmail, e)
            false
        }
    }

    override fun sendSituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: java.math.BigDecimal,
        unpaidInvoicesCount: Int,
        activeSubscriptionsCount: Int,
        activeSubscriptionNames: List<String>
    ): Boolean {
        return try {
            val context = Context()
            context.setVariable("name", subscriberName)
            context.setVariable("totalOwed", totalOwed)
            context.setVariable("unpaidInvoicesCount", unpaidInvoicesCount)
            context.setVariable("activeSubscriptionsCount", activeSubscriptionsCount)
            context.setVariable("activeSubscriptionNames", activeSubscriptionNames)

            val htmlContent = templateEngine.process("situation-email", context)
            val subject = "Your Subscription Account Status"

            sendEmail(toEmail, subscriberName, subject, htmlContent)
        } catch (e: Exception) {
            log.error("Failed to send situation email to {}", toEmail, e)
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
