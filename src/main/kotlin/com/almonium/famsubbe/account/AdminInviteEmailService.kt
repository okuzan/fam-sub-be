package com.almonium.famsubbe.account

import com.almonium.famsubbe.config.AppEmailProperties
import com.almonium.famsubbe.config.ZeptoMailProperties
import com.almonium.famsubbe.invoice.EmailLogo
import com.almonium.famsubbe.invoice.emailContext
import com.almonium.famsubbe.invoice.replyTo
import com.almonium.famsubbe.util.HtmlFileWriter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.thymeleaf.TemplateEngine

@Service
class AdminInviteEmailService(
    private val htmlFileWriter: HtmlFileWriter,
    private val zeptoProperties: ZeptoMailProperties,
    private val appEmailProperties: AppEmailProperties,
    private val templateEngine: TemplateEngine,
    @Value($$"${app.web-domain}") private val webDomain: String
) {
    private val log = LoggerFactory.getLogger(AdminInviteEmailService::class.java)
    private val restClient = RestClient.create()

    fun sendInvite(email: String, inviteUrl: String): Boolean {
        val subject = "You’re invited to be a FamSub admin"
        val context = emailContext(
            subject,
            "Accept with $email. Ignore this if you weren’t expecting it.",
            webDomain
        )
        context.setVariable("email", email)
        context.setVariable("inviteUrl", inviteUrl)
        val htmlContent = templateEngine.process("admin-invite-email", context)

        if (appEmailProperties.dryRun) {
            log.info("Email sending is disabled. Skipping admin invite email to {}", email)
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
                        "address" to email,
                        "name" to email
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
                log.info("Successfully sent admin invite email to {}", email)
            } else {
                log.error("Failed to send admin invite email to {}. Status code: {}", email, response.statusCode)
            }
            success
        } catch (e: Exception) {
            log.error("Error sending admin invite email to {}", email, e)
            false
        }
    }
}
