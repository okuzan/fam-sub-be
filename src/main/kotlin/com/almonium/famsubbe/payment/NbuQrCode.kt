package com.almonium.famsubbe.payment

import java.math.BigDecimal
import java.util.*

/**
 * The National Bank of Ukraine credit-transfer QR code, format version 002
 * (NBU Board Resolution No. 68 of 28.05.2020, appendix 2). The result is a
 * https://bank.gov.ua/qr/ link: rendered as a QR code it is scanned by any
 * Ukrainian banking app, and opened on a phone it launches one, in both cases
 * with recipient, amount and purpose filled in.
 */
object NbuQrCode {
    private const val LINK_PREFIX = "https://bank.gov.ua/qr/"
    private const val MAX_RECIPIENT_LENGTH = 38
    private const val MAX_PURPOSE_LENGTH = 140

    /**
     * @param recipientCode the recipient's РНОКПП (individual) or ЄДРПОУ
     * (company); the format makes it mandatory.
     * @param amount null leaves the amount for the payer to enter.
     */
    fun link(
        recipientName: String,
        iban: String,
        recipientCode: String,
        purpose: String,
        amount: BigDecimal?
    ): String {
        require(recipientCode.isNotBlank()) { "The NBU QR code needs the recipient's tax number" }

        val fields = listOf(
            "BCD",          // service tag
            "002",          // format version
            "1",            // character set: UTF-8
            "UCT",          // function: Ukrainian credit transfer
            "",             // BIC: reserved in version 002
            recipientName.trim().take(MAX_RECIPIENT_LENGTH),
            iban.replace(" ", "").uppercase(),
            amount?.let { "UAH${formatAmount(it)}" }.orEmpty(),
            recipientCode.trim(),
            "",             // purpose code: reserved
            "",             // reference: reserved
            purpose.trim().take(MAX_PURPOSE_LENGTH),
            ""              // display text
        )
        val payload = fields.joinToString(separator = "\n", postfix = "\n")
        return LINK_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    /** "UAH3" rather than "UAH3.00", but kopiyky always as two digits: "UAH31.90". */
    internal fun formatAmount(amount: BigDecimal): String {
        require(amount > BigDecimal.ZERO) { "QR amount must be positive" }
        val scaled = amount.setScale(2, java.math.RoundingMode.UNNECESSARY)
        return if (scaled.stripTrailingZeros().scale() <= 0) {
            scaled.toBigInteger().toString()
        } else {
            scaled.toPlainString()
        }
    }
}
