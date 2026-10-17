package com.almonium.famsubbe.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.mail.zepto")
data class ZeptoMailProperties(
    val apiUrl: String,
    val apiKey: String,
    val fromAddress: String,
    val fromName: String
)

@ConfigurationProperties(prefix = "app.email")
data class AppEmailProperties(
    val dryRun: Boolean = false,
    /** The family inbox replies go to. The from-address is not read, so every email sets this as Reply-To. */
    val replyTo: String = "family@almonium.com"
)

/** Where subscribers pay: the one Mono account that BankSyncService reads. */
@ConfigurationProperties(prefix = "app.payment")
data class PaymentProperties(
    val recipientName: String,
    val iban: String,
    val cardNumber: String,
    /** The recipient's РНОКПП, which the NBU QR code requires. Blank: the pay page shows no QR. */
    val recipientTaxId: String = ""
)
