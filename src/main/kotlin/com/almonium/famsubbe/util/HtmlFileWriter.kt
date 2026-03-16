package com.almonium.famsubbe.util

import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths

@Component
class HtmlFileWriter {

    fun saveHtmlToFile(htmlContent: String, prefix: String = "rendered_email") {
        val tempDir = Paths.get("temp")
        if (!Files.exists(tempDir)) {
            Files.createDirectory(tempDir)
        }
        
        val filename = "${prefix}_${System.currentTimeMillis()}.html"
        Files.writeString(tempDir.resolve(filename), htmlContent)
    }
}
