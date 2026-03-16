package com.almonium.famsubbe.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.mail.zepto")
data class ZeptoMailProperties(
    val apiUrl: String,
    val apiKey: String,
    val fromAddress: String,
    val fromName: String
)

@ConfigurationProperties(prefix = "app.email")
data class AppEmailProperties(
    val dryRun: Boolean = false
)
