package com.almonium.famsubbe.invoice

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class InvoiceEmailSubjectTest {

    @Test
    fun `manual invoice subject leads with its normalized notes`() {
        val invoice = invoice(
            origin = InvoiceOrigin.MANUAL,
            amount = "450",
            fromMonth = "2026-07",
            toMonth = "2026-07",
            notes = "  Netflix\nannual adjustment  "
        )

        assertEquals(
            "Netflix annual adjustment: 450,00\u00a0₴",
            buildInvoiceEmailSubject(invoice)
        )
    }

    @Test
    fun `ledger invoice subject collapses equal months`() {
        val invoice = invoice(
            origin = InvoiceOrigin.SUBSCRIPTION_LEDGER,
            amount = "450.00",
            fromMonth = "2026-07",
            toMonth = "2026-07"
        )

        assertEquals(
            "Your Jul 2026 share: 450,00\u00a0₴",
            buildInvoiceEmailSubject(invoice)
        )
    }

    @Test
    fun `ledger invoice subject shows a compact same-year range`() {
        val invoice = invoice(
            origin = InvoiceOrigin.SUBSCRIPTION_LEDGER,
            amount = "450.00",
            fromMonth = "2026-06",
            toMonth = "2026-07"
        )

        assertEquals(
            "Your Jun–Jul 2026 share: 450,00\u00a0₴",
            buildInvoiceEmailSubject(invoice)
        )
    }

    @Test
    fun `ledger invoice subject includes both years for a cross-year range`() {
        val invoice = invoice(
            origin = InvoiceOrigin.SUBSCRIPTION_LEDGER,
            amount = "450.00",
            fromMonth = "2025-12",
            toMonth = "2026-01"
        )

        assertEquals(
            "Your Dec 2025–Jan 2026 share: 450,00\u00a0₴",
            buildInvoiceEmailSubject(invoice)
        )
    }

    @Test
    fun `an invoice that is already paid is a receipt`() {
        val invoice = invoice(
            origin = InvoiceOrigin.SUBSCRIPTION_LEDGER,
            amount = "117.00",
            fromMonth = "2026-09",
            toMonth = "2026-10"
        ).apply { status = InvoiceStatus.PAID }

        assertEquals("Receipt for Sep–Oct 2026: nothing to pay", buildInvoiceEmailSubject(invoice))
    }

    @Test
    fun `money groups thousands with non-breaking spaces`() {
        assertEquals("1\u00a0968,00\u00a0₴", EmailFormat.money(BigDecimal("1968")))
        assertEquals("−40,00\u00a0₴", EmailFormat.minus(BigDecimal("40")))
    }

    private fun invoice(
        origin: InvoiceOrigin,
        amount: String,
        fromMonth: String,
        toMonth: String,
        notes: String? = null
    ) = Invoice().apply {
        this.origin = origin
        this.totalAmount = BigDecimal(amount)
        this.fromMonth = YearMonth.parse(fromMonth)
        this.toMonth = YearMonth.parse(toMonth)
        this.invoiceDate = LocalDate.of(2026, 7, 1)
        this.notes = notes
    }
}
