package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.accounting.LedgerEntry
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
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InvoiceServiceStatusTest {
    private val invoiceRepository = mock(InvoiceRepository::class.java)
    private val ledgerEntryRepository = mock(LedgerEntryRepository::class.java)
    private val subscriberRepository = mock(SubscriberRepository::class.java)
    private val events = mutableListOf<Any>()
    private val service = InvoiceService(
        mock(InvoiceGenerationRunRepository::class.java),
        invoiceRepository,
        ledgerEntryRepository,
        subscriberRepository
    ) { events += it }

    private val subscriber = Subscriber().apply {
        id = UUID.randomUUID()
        name = "Alice"
        email = "alice@example.com"
        balance = BigDecimal.ZERO
    }

    init {
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer {
            it.getArgument<Invoice>(0).apply { if (id == null) id = UUID.randomUUID() }
        }
    }

    @Test
    fun `setting VOID through the status endpoint releases the ledger entries`() {
        val invoice = invoice(InvoiceStatus.SENT)
        val entry = LedgerEntry().apply { this.invoice = invoice }
        `when`(invoiceRepository.findById(invoice.id!!)).thenReturn(Optional.of(invoice))
        `when`(ledgerEntryRepository.findByInvoice(invoice)).thenReturn(listOf(entry))

        val result = service.updateStatus(invoice.id!!, InvoiceStatus.VOID).invoice

        assertEquals(InvoiceStatus.VOID.name, result.status)
        assertNull(entry.invoice)
    }

    @Test
    fun `voiding an emailed invoice announces it, voiding a draft does not`() {
        val sent = invoice(InvoiceStatus.SENT)
        val draft = invoice(InvoiceStatus.DRAFT)
        listOf(sent, draft).forEach { `when`(invoiceRepository.findById(it.id!!)).thenReturn(Optional.of(it)) }

        service.voidInvoice(sent.id!!, "Wrong price")
        service.voidInvoice(draft.id!!, null)

        assertEquals(listOf<Any>(SentInvoiceVoidedEvent(sent.id!!)), events)
        assertEquals("Wrong price", voidReason(sent))
    }

    @Test
    fun `a voided invoice cannot be reopened`() {
        val invoice = invoice(InvoiceStatus.VOID)
        `when`(invoiceRepository.findById(invoice.id!!)).thenReturn(Optional.of(invoice))

        assertFailsWith<IllegalStateException> {
            service.updateStatus(invoice.id!!, InvoiceStatus.DRAFT)
        }
        assertEquals(InvoiceStatus.VOID, invoice.status)
    }

    @Test
    fun `reopening an invoice paid from balance refunds the balance`() {
        subscriber.balance = BigDecimal("150.00")
        val invoice = invoice(InvoiceStatus.SENT)
        `when`(invoiceRepository.findById(invoice.id!!)).thenReturn(Optional.of(invoice))
        `when`(subscriberRepository.save(any(Subscriber::class.java))).thenAnswer { it.getArgument<Subscriber>(0) }

        service.payFromBalance(invoice.id!!)
        assertEquals(BigDecimal("50.00"), subscriber.balance)
        assertEquals(BigDecimal("100.00"), invoice.balancePaidAmount)

        val change = service.updateStatus(invoice.id!!, InvoiceStatus.SENT)

        assertEquals(BigDecimal("100.00"), change.balanceRefunded)
        assertEquals(BigDecimal("150.00"), subscriber.balance)
        assertNull(invoice.balancePaidAmount)
        assertEquals(InvoiceStatus.SENT, invoice.status)
    }

    @Test
    fun `reopening an invoice paid some other way refunds nothing`() {
        subscriber.balance = BigDecimal("20.00")
        val invoice = invoice(InvoiceStatus.PAID)
        `when`(invoiceRepository.findById(invoice.id!!)).thenReturn(Optional.of(invoice))

        val change = service.updateStatus(invoice.id!!, InvoiceStatus.DRAFT)

        assertNull(change.balanceRefunded)
        assertEquals(BigDecimal("20.00"), subscriber.balance)
    }

    private fun invoice(status: InvoiceStatus) = Invoice().apply {
        id = UUID.randomUUID()
        subscriber = this@InvoiceServiceStatusTest.subscriber
        fromMonth = YearMonth.of(2026, 9)
        toMonth = YearMonth.of(2026, 9)
        invoiceDate = LocalDate.of(2026, 10, 1)
        totalAmount = BigDecimal("100.00")
        this.status = status
        createdAt = Instant.now()
        statusChangedAt = Instant.now()
        createdByAccountId = UUID.randomUUID()
    }
}
