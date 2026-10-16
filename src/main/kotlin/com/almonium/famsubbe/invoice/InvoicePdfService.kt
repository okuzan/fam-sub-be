package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
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
    fun generateInvoicePdf(invoiceId: UUID): ByteArray {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        val entries = ledgerEntryRepository.findByInvoiceId(invoiceId)

        val outputStream = ByteArrayOutputStream()
        val writer = PdfWriter(outputStream)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)

        // Read the font as bytes rather than resolving it to a filesystem path.
        // URL.toURI().path is null for a `jar:` URL and mangles any path with
        // spaces or non-ASCII characters, so the path form only works by accident
        // of the image running the application exploded.
        val fontBytes = javaClass.classLoader.getResourceAsStream("fonts/NotoSans.ttf")
            ?.use { it.readBytes() }
            ?: throw IllegalStateException("Font file not found: fonts/NotoSans.ttf")

        val font: PdfFont = PdfFontFactory.createFont(
            fontBytes,
            PdfEncodings.IDENTITY_H,
            PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
        )

        document.add(Paragraph("Invoice #${invoice.id}").setFont(font))
        document.add(Paragraph("Subscriber: ${invoice.subscriber?.name ?: ""}").setFont(font))
        document.add(Paragraph("Invoice date: ${invoice.invoiceDate}").setFont(font))
        document.add(Paragraph("Period: ${invoice.fromMonth} - ${invoice.toMonth}").setFont(font))
        document.add(Paragraph("Origin: ${invoice.origin.name}").setFont(font))
        document.add(Paragraph("Total: ₴${invoice.totalAmount}").setFont(font))
        invoice.notes?.takeIf { it.isNotBlank() }?.let {
            document.add(Paragraph("Notes: $it").setFont(font))
        }
        document.add(Paragraph(" ").setFont(font))

        if (entries.isEmpty()) {
            document.add(Paragraph("No ledger entries attached to this invoice.").setFont(font))
        } else {
            document.add(Paragraph("Ledger Entries:").setFont(font))
            entries.forEach { entry ->
                document.add(
                    Paragraph("- ${entry.subscriptionService?.name ?: ""}: ₴${entry.amount} (${entry.recordedMonth})")
                        .setFont(font)
                )
            }
        }

        document.close()
        return outputStream.toByteArray()
    }
}
