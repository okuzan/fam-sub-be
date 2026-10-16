package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.accounting.LedgerEntry
import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.lowagie.text.Document
import com.lowagie.text.Font
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.util.*

@Service
@Transactional(readOnly = true)
class InvoicePdfService(
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) {
    // Read the font as bytes rather than resolving it to a filesystem path.
    // URL.toURI().path is null for a `jar:` URL and mangles any path with
    // spaces or non-ASCII characters, so the path form only works by accident
    // of the image running the application exploded.
    private val fontBytes: ByteArray by lazy {
        javaClass.classLoader.getResourceAsStream(FONT_PATH)
            ?.use { it.readBytes() }
            ?: throw IllegalStateException("Font file not found: $FONT_PATH")
    }

    fun generateInvoicePdf(invoiceId: UUID): ByteArray {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        return render(invoice, ledgerEntryRepository.findByInvoiceId(invoiceId))
    }

    internal fun render(invoice: Invoice, entries: List<LedgerEntry>): ByteArray {
        // Identity-H with the embedded Noto Sans covers Cyrillic names and the ₴ sign.
        val baseFont = BaseFont.createFont(FONT_PATH, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null)
        val font = Font(baseFont, 12f)

        val outputStream = ByteArrayOutputStream()
        val document = Document()
        PdfWriter.getInstance(document, outputStream)
        document.open()

        fun line(text: String) = document.add(Paragraph(text, font))

        line("Invoice #${invoice.id}")
        line("Subscriber: ${invoice.subscriber?.name.orEmpty()}")
        line("Invoice date: ${invoice.invoiceDate}")
        line("Period: ${invoice.fromMonth} - ${invoice.toMonth}")
        line("Origin: ${invoice.origin.name}")
        line("Total: ₴${invoice.totalAmount}")
        invoice.notes?.takeIf { it.isNotBlank() }?.let { line("Notes: $it") }
        line(" ")

        if (entries.isEmpty()) {
            line("No ledger entries attached to this invoice.")
        } else {
            line("Ledger Entries:")
            entries.forEach { entry ->
                line("- ${entry.subscriptionService?.name.orEmpty()}: ₴${entry.amount} (${entry.recordedMonth})")
            }
        }

        document.close()
        return outputStream.toByteArray()
    }

    private companion object {
        const val FONT_PATH = "fonts/NotoSans.ttf"
    }
}
