package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals

class InvoiceServiceIncomingPaymentTest {
    private val invoiceRepository = mock(InvoiceRepository::class.java)
    private val subscriberRepository = mock(SubscriberRepository::class.java)
    private val service = InvoiceService(
        mock(InvoiceGenerationRunRepository::class.java),
        invoiceRepository,
        mock(LedgerEntryRepository::class.java),
        subscriberRepository
    )

    private val subscriber = Subscriber().apply {
        id = UUID.randomUUID()
        name = "Роман"
        email = "roman@example.com"
        balance = BigDecimal.ZERO
    }

    init {
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer { it.getArgument<Invoice>(0) }
        `when`(subscriberRepository.save(any(Subscriber::class.java))).thenAnswer { it.getArgument<Subscriber>(0) }
    }

    @Test
    fun `an overpayment settles the invoice and keeps the rest as credit`() {
        val invoice = invoice("103.60", LocalDate.of(2026, 8, 7))
        unpaid(invoice)

        val result = service.applyIncomingPayment(subscriber, BigDecimal("104.00"))

        assertEquals(listOf(invoice.id), result.settledInvoiceIds)
        assertEquals(InvoiceStatus.PAID, invoice.status)
        assertEquals(BigDecimal("103.60"), invoice.balancePaidAmount)
        assertEquals(BigDecimal("0.40"), subscriber.balance)
    }

    @Test
    fun `an invoice of exactly the paid amount is settled first`() {
        val older = invoice("500.00", LocalDate.of(2026, 8, 7))
        val exact = invoice("31.93", LocalDate.of(2026, 9, 22))
        unpaid(older, exact)

        val result = service.applyIncomingPayment(subscriber, BigDecimal("31.93"))

        assertEquals(listOf(exact.id), result.settledInvoiceIds)
        assertEquals(InvoiceStatus.SENT, older.status)
        assertEquals(BigDecimal("0.00"), subscriber.balance)
    }

    @Test
    fun `the rest is settled oldest first and stops at the first it cannot cover`() {
        subscriber.balance = BigDecimal("10.00")
        val oldest = invoice("60.00", LocalDate.of(2026, 7, 1))
        val middle = invoice("80.00", LocalDate.of(2026, 8, 1))
        val newest = invoice("5.00", LocalDate.of(2026, 9, 1))
        unpaid(newest, middle, oldest)

        val result = service.applyIncomingPayment(subscriber, BigDecimal("100.00"))

        assertEquals(listOf(oldest.id), result.settledInvoiceIds)
        assertEquals(InvoiceStatus.SENT, middle.status)
        assertEquals(InvoiceStatus.SENT, newest.status)
        assertEquals(BigDecimal("50.00"), result.balanceAfter)
    }

    private fun unpaid(vararg invoices: Invoice) {
        `when`(invoiceRepository.findBySubscriberAndStatusIn(subscriber, listOf(InvoiceStatus.DRAFT, InvoiceStatus.SENT)))
            .thenReturn(invoices.toList())
    }

    private fun invoice(amount: String, date: LocalDate) = Invoice().apply {
        id = UUID.randomUUID()
        subscriber = this@InvoiceServiceIncomingPaymentTest.subscriber
        fromMonth = YearMonth.from(date)
        toMonth = YearMonth.from(date)
        invoiceDate = date
        totalAmount = BigDecimal(amount)
        status = InvoiceStatus.SENT
        createdAt = Instant.now()
        statusChangedAt = Instant.now()
        createdByAccountId = UUID.randomUUID()
    }
}
