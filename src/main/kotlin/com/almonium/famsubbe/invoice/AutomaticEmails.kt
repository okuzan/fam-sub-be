package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.payment.PaymentDetails
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** An invoice the subscriber had already been emailed was voided; they get told once the void commits. */
data class SentInvoiceVoidedEvent(val invoiceId: UUID)

/** What the payment-received email says about one applied bank transfer. */
data class PaymentReceivedEmail(
    val toEmail: String,
    val name: String,
    val amount: BigDecimal,
    val arrivedAt: Instant,
    /** The sender as the bank names them, which is not always the subscriber. */
    val senderName: String?,
    /** The invoices this transfer paid, oldest first. */
    val settled: List<EmailInvoiceLine>,
    /** Invoices still unpaid after it, oldest first. */
    val stillOpen: List<EmailInvoiceLine>,
    /** Credit the subscriber already held that went into the invoices this transfer paid. */
    val creditUsed: BigDecimal,
    val balanceAfter: BigDecimal,
    val toPay: BigDecimal,
    val payment: PaymentDetails
)

/** One invoice as an email names it: "Aug 2026", "Netflix Premium, Spotify Family", 186,00 ₴. */
data class EmailInvoiceLine(val title: String, val detail: String, val amount: BigDecimal)

private const val VOIDED_PREFIX = "Voided: "

/** "Sep–Oct 2026" for a ledger invoice, its own note for a one-off. */
internal fun emailInvoiceTitle(invoice: Invoice): String =
    if (invoice.origin == InvoiceOrigin.MANUAL) {
        invoice.notes.orEmpty().lines()
            .filterNot { it.startsWith(VOIDED_PREFIX) }
            .joinToString(" ") { it.trim() }
            .trim()
            .ifEmpty { "One-off" }
    } else {
        formatInvoicePeriod(invoice.fromMonth, invoice.toMonth)
    }

/** The reason given when the invoice was voided, if any (see [InvoiceService.voidInvoice]). */
internal fun voidReason(invoice: Invoice): String? =
    invoice.notes.orEmpty().lines()
        .lastOrNull { it.startsWith(VOIDED_PREFIX) }
        ?.removePrefix(VOIDED_PREFIX)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
