package com.almonium.famsubbe.service

import com.almonium.famsubbe.entity.Invoice
import com.almonium.famsubbe.entity.LedgerEntry
import org.springframework.context.annotation.Profile
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value

@Service
class DefaultInvoiceEmailService(
    private val mailSender: JavaMailSender,
    private val templateEngine: TemplateEngine,
    @Value("\${spring.mail.username}") private val fromEmail: String
) : InvoiceEmailService {

    private val log = LoggerFactory.getLogger(DefaultInvoiceEmailService::class.java)

    override fun sendInvoiceEmail(invoice: Invoice, entries: List<LedgerEntry>): Boolean {
        val subscriber = invoice.subscriber ?: return false
        val toEmail = subscriber.email ?: return false

        return try {
            val context = Context()
            context.setVariable("invoice", invoice)
            context.setVariable("subscriber", subscriber)
            context.setVariable("entries", entries)

            val htmlContent = templateEngine.process("invoice-email", context)

            val mimeMessage = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")

            helper.setFrom(fromEmail)
            helper.setTo(toEmail)
            helper.setSubject("Your Invoice for ${invoice.fromMonth} - ${invoice.toMonth}")
            helper.setText(htmlContent, true)

            mailSender.send(mimeMessage)
            true
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
        activeSubscriptionsCount: Int
    ): Boolean {
        return try {
            val context = Context()
            context.setVariable("name", subscriberName)
            context.setVariable("totalOwed", totalOwed)
            context.setVariable("unpaidInvoicesCount", unpaidInvoicesCount)
            context.setVariable("activeSubscriptionsCount", activeSubscriptionsCount)

            val htmlContent = templateEngine.process("situation-email", context)

            val mimeMessage = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")

            helper.setFrom(fromEmail)
            helper.setTo(toEmail)
            helper.setSubject("Your Subscription Account Status")
            helper.setText(htmlContent, true)

            mailSender.send(mimeMessage)
            true
        } catch (e: Exception) {
            log.error("Failed to send situation email to {}", toEmail, e)
            false
        }
    }
}
