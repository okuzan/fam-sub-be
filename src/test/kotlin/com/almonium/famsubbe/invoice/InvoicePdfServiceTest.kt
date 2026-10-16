package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.accounting.LedgerEntry
import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscription.SubscriptionService
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertTrue

class InvoicePdfServiceTest {
    private val service = InvoicePdfService(
        mock(InvoiceRepository::class.java),
        mock(LedgerEntryRepository::class.java)
    )

    @Test
    fun `renders Cyrillic names and the hryvnia sign`() {
        val invoice = Invoice().apply {
            id = UUID.randomUUID()
            subscriber = Subscriber().apply { name = "Олег" }
            fromMonth = YearMonth.of(2026, 9)
            toMonth = YearMonth.of(2026, 9)
            invoiceDate = LocalDate.of(2026, 10, 1)
            totalAmount = BigDecimal("123.45")
            notes = "Нотатка"
        }
        val entry = LedgerEntry().apply {
            subscriptionService = SubscriptionService().apply { name = "YouTube" }
            amount = BigDecimal("123.45")
            recordedMonth = YearMonth.of(2026, 9)
        }

        val pdf = service.render(invoice, listOf(entry))

        val text = PdfTextExtractor(PdfReader(pdf)).getTextFromPage(1)
        assertTrue("Олег" in text, text)
        assertTrue("₴123.45" in text, text)
        assertTrue("YouTube" in text, text)
    }
}
