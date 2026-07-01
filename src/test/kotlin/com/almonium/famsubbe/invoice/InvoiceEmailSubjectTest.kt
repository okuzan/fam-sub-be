package com.almonium.famsubbe.invoice

import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class InvoiceEmailSubjectTest {

    @Test
    fun `manual invoice subject includes amount and normalized notes`() {
        val invoice = invoice(
            origin = InvoiceOrigin.MANUAL,
            amount = "450",
            fromMonth = "2026-07",
            toMonth = "2026-07",
            notes = "  Netflix\nannual adjustment  "
        )

        assertEquals(
            "New Invoice: ₴450.00 — Netflix annual adjustment",
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
            "New Invoice: ₴450.00 — Subscriptions, Jul 2026",
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
            "New Invoice: ₴450.00 — Subscriptions, Jun–Jul 2026",
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
            "New Invoice: ₴450.00 — Subscriptions, Dec 2025–Jan 2026",
            buildInvoiceEmailSubject(invoice)
        )
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
        this.notes = notes
    }
}
