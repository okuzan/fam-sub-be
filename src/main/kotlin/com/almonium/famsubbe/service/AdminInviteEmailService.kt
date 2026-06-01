package com.almonium.famsubbe.service

import com.almonium.famsubbe.config.AppEmailProperties
import com.almonium.famsubbe.config.ZeptoMailProperties
import com.almonium.famsubbe.util.HtmlFileWriter
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class AdminInviteEmailService(
    private val htmlFileWriter: HtmlFileWriter,
    private val zeptoProperties: ZeptoMailProperties,
    private val appEmailProperties: AppEmailProperties
) {
    private val log = LoggerFactory.getLogger(AdminInviteEmailService::class.java)
    private val restClient = RestClient.create()

    fun sendInvite(email: String, inviteUrl: String): Boolean {
        val subject = "FamSub admin invitation"
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <h2>FamSub admin invitation</h2>
                <p>You have been invited to become a FamSub administrator.</p>
                <p><a href="$inviteUrl">Accept admin invitation</a></p>
                <p>This invitation can only be accepted with this Google account: <strong>$email</strong>.</p>
                <p>If you did not expect this invitation, you can ignore this email.</p>
            </body>
            </html>
        """.trimIndent()

        if (appEmailProperties.dryRun) {
            log.info("Email sending is disabled. Skipping admin invite email to {}", email)
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
                        "address" to email,
                        "name" to email
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
