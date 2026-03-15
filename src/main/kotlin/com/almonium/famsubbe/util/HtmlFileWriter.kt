package com.almonium.famsubbe.util

import jakarta.mail.BodyPart
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64

@Component
class HtmlFileWriter {

    fun saveMimeMessageToFile(mimeMessage: MimeMessage) {
        val outputStream = ByteArrayOutputStream()
        mimeMessage.writeTo(outputStream)

        val content = parseMultipart(mimeMessage.content as MimeMultipart)
        
        val tempDir = Paths.get("temp")
        if (!Files.exists(tempDir)) {
            Files.createDirectory(tempDir)
        }
        
        Files.write(tempDir.resolve("rendered_email.html"), content.toByteArray())
    }

    private fun parseMultipart(mimeMultipart: MimeMultipart): String {
        val htmlContent = StringBuilder()

        for (i in 0 until mimeMultipart.count) {
            val bodyPart: BodyPart = mimeMultipart.getBodyPart(i)

            if (bodyPart.isMimeType("multipart/*")) {
                val nestedMultipart = bodyPart.content as MimeMultipart
                htmlContent.append(parseMultipart(nestedMultipart))
            } else if (bodyPart.isMimeType(MediaType.TEXT_HTML_VALUE)) {
                htmlContent.append(bodyPart.content as String)
            } else if (bodyPart.isMimeType(MediaType.IMAGE_PNG_VALUE) ||
                bodyPart.isMimeType(MediaType.IMAGE_JPEG_VALUE) ||
                bodyPart.isMimeType("image/svg+xml")
            ) {
                val contentIdArray = bodyPart.getHeader("Content-ID")
                if (!contentIdArray.isNullOrEmpty()) {
                    val contentId = contentIdArray[0].replace("[<>]".toRegex(), "")
                    val base64Image = encodeImageToBase64(bodyPart as MimeBodyPart)
                    val base64Tag = "data:" + bodyPart.contentType + ";base64," + base64Image
                    val imageTag = "cid:$contentId"
                    val startIndex = htmlContent.indexOf(imageTag)
                    
                    if (startIndex != -1) {
                        htmlContent.replace(startIndex, startIndex + imageTag.length, base64Tag)
                    } else {
                        htmlContent.append("<img src=\"").append(base64Tag).append("\" alt=\"Embedded Image\"/>")
                    }
                }
            }
        }

        return htmlContent.toString()
    }

    private fun encodeImageToBase64(bodyPart: MimeBodyPart): String {
        val outputStream = ByteArrayOutputStream()
        bodyPart.dataHandler.writeTo(outputStream)
        val imageBytes = outputStream.toByteArray()
        return Base64.getEncoder().encodeToString(imageBytes)
    }
}
