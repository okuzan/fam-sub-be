package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.util.BillingTime
import org.springframework.core.io.ClassPathResource
import org.thymeleaf.context.Context
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale

/** How every email writes money, months and dates. Templates reach it as `fmt`. */
object EmailFormat {
    private const val NBSP = ' '

    /** 1 968,00 ₴, with non-breaking spaces so an amount never wraps. */
    fun money(amount: BigDecimal?): String {
        val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
            groupingSeparator = NBSP
            decimalSeparator = ','
            minusSign = '−'
        }
        val value = (amount ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        return DecimalFormat("#,##0.00", symbols).format(value) + "$NBSP₴"
    }

    /** A credit shown as taken off a total: −40,00 ₴. */
    fun minus(amount: BigDecimal?): String = money((amount ?: BigDecimal.ZERO).abs().negate())

    fun period(from: YearMonth?, to: YearMonth?): String = formatInvoicePeriod(from, to)

    /** The same, for the "2026-08" strings the situation DTOs carry. */
    fun periodOf(from: String, to: String): String = period(YearMonth.parse(from), YearMonth.parse(to))

    /** September 2026 */
    fun month(month: YearMonth?): String = requireNotNull(month).format(pattern("MMMM yyyy"))

    /** Aug */
    fun shortMonth(month: String): String = YearMonth.parse(month).format(pattern("MMM"))

    /** Feb 2025 */
    fun monthYear(month: String): String = YearMonth.parse(month).format(pattern("MMM yyyy"))

    /** 1 Nov 2026 */
    fun date(date: LocalDate?): String = requireNotNull(date).format(pattern("d MMM yyyy"))

    /** 2 Sep */
    fun dayMonth(date: LocalDate?): String = requireNotNull(date).format(pattern("d MMM"))

    fun daysOpen(since: LocalDate): Long = ChronoUnit.DAYS.between(since, BillingTime.today())

    /** Netflix · Netflix and Spotify · Netflix, Spotify and YouTube */
    fun joinNatural(items: List<String>): String =
        if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " and " + items.last()

    private fun pattern(pattern: String) = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
}

/** What `email-layout.html` expects of every email, before the template adds its own variables. */
internal fun emailContext(
    subject: String,
    preheader: String,
    webDomain: String,
    footerNote: String? = null
): Context = Context().apply {
    setVariable("subject", subject)
    setVariable("preheader", preheader)
    setVariable("footerNote", footerNote)
    setVariable("webDomain", webDomain)
    setVariable("webHost", webDomain.substringAfter("://"))
    setVariable("today", BillingTime.today())
    setVariable("fmt", EmailFormat)
}

/**
 * The FamSub mark in the email header. Gmail blocks SVG and data URIs, so it
 * travels with each message as an inline image the templates reference by CID.
 */
object EmailLogo {
    const val CID = "famsub-mark"

    private val base64: String by lazy {
        ClassPathResource("email/famsub-mark.png").inputStream.use { Base64.getEncoder().encodeToString(it.readAllBytes()) }
    }

    /** The ZeptoMail `inline_images` entry. */
    fun inlineImages(): List<Map<String, String>> =
        listOf(mapOf("cid" to CID, "mime_type" to "image/png", "content" to base64))

    /** A dry-run file has no attachments, so the preview embeds the image instead. */
    fun embedForPreview(html: String): String = html.replace("cid:$CID", "data:image/png;base64,$base64")
}

/** The ZeptoMail `reply_to` entry that sends replies to the family inbox. */
internal fun replyTo(address: String): List<Map<String, String>> = listOf(mapOf("address" to address, "name" to "FamSub"))
