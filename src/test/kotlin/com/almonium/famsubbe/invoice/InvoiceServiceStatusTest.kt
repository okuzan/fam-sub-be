package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.accounting.LedgerEntry
import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import com.almonium.famsubbe.subscription.SubscriptionService
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

    @Test
    fun `voiding a paid invoice credits its amount to the balance and releases the ledger`() {
        subscriber.balance = BigDecimal("20.00")
        val bankTransactionId = UUID.randomUUID()
        val invoice = invoice(InvoiceStatus.PAID).apply {
            sentAt = Instant.now()
            balancePaidAmount = BigDecimal("100.00")
            paidByBankTransactionId = bankTransactionId
        }
        val entry = LedgerEntry().apply { this.invoice = invoice }
        `when`(invoiceRepository.findById(invoice.id!!)).thenReturn(Optional.of(invoice))
        `when`(ledgerEntryRepository.findByInvoice(invoice)).thenReturn(listOf(entry))

        val result = service.voidInvoice(invoice.id!!, "Charged twice")

        assertEquals(BigDecimal("100.00"), result.balanceCredited)
        assertEquals(BigDecimal("120.00"), subscriber.balance)
        assertEquals(InvoiceStatus.VOID, invoice.status)
        assertNull(invoice.balancePaidAmount)
        assertNull(entry.invoice)
        // Kept so undoing that bank payment still debits what it credited.
        assertEquals(bankTransactionId, invoice.paidByBankTransactionId)
        // "Please don't pay it" would be wrong for an invoice already paid.
        assertEquals(emptyList(), events)
    }

    @Test
    fun `voiding a paid invoice of a billing-exempt subscriber credits nothing`() {
        subscriber.billingExempt = true
        val invoice = invoice(InvoiceStatus.PAID)
        `when`(invoiceRepository.findById(invoice.id!!)).thenReturn(Optional.of(invoice))

        val result = service.voidInvoice(invoice.id!!, null)

        assertNull(result.balanceCredited)
        assertEquals(BigDecimal.ZERO, subscriber.balance)
        assertEquals(InvoiceStatus.VOID, invoice.status)
    }

    @Test
    fun `voiding a waived invoice credits nothing even after the exemption ends`() {
        val invoice = invoice(InvoiceStatus.PAID).apply { notes = InvoiceService.BILLING_EXEMPT_NOTE }
        `when`(invoiceRepository.findById(invoice.id!!)).thenReturn(Optional.of(invoice))

        assertNull(service.voidInvoice(invoice.id!!, null).balanceCredited)
        assertEquals(BigDecimal.ZERO, subscriber.balance)
    }

    @Test
    fun `setting VOID on a paid invoice through the status endpoint reports the credit`() {
        val invoice = invoice(InvoiceStatus.PAID)
        `when`(invoiceRepository.findById(invoice.id!!)).thenReturn(Optional.of(invoice))

        val change = service.updateStatus(invoice.id!!, InvoiceStatus.VOID)

        assertEquals(BigDecimal("100.00"), change.balanceRefunded)
        assertEquals(BigDecimal("100.00"), subscriber.balance)
    }

    @Test
    fun `writing off a line voids the invoice and reissues the rest`() {
        val invoice = invoice(InvoiceStatus.SENT).apply {
            origin = InvoiceOrigin.SUBSCRIPTION_LEDGER
            totalAmount = BigDecimal("2185.43")
        }
        val claude = line(invoice, "Claude", "1830.00")
        val chatGpt = line(invoice, "ChatGPT", "230.30")
        val youTube = line(invoice, "YouTube", "125.13")
        `when`(invoiceRepository.findById(invoice.id!!)).thenReturn(Optional.of(invoice))
        `when`(ledgerEntryRepository.findByInvoice(invoice)).thenReturn(listOf(claude, chatGpt, youTube))

        val result = service.writeOffLines(invoice.id!!, setOf(claude.id!!), "Left Claude in October", UUID.randomUUID())

        assertEquals(InvoiceStatus.VOID, invoice.status)
        assertEquals("Claude November 2026 removed: Left Claude in October", voidReason(invoice))
        // The written-off line stays on the voided invoice, so it is never billed again.
        assertEquals(invoice, claude.invoice)
        assertEquals("Left Claude in October", claude.writtenOffReason)
        val reissued = requireNotNull(result.reissuedInvoice)
        assertEquals(BigDecimal("355.43"), reissued.totalAmount)
        assertEquals(InvoiceStatus.DRAFT.name, reissued.status)
        assertEquals(reissued.id, chatGpt.invoice?.id)
        assertEquals(reissued.id, youTube.invoice?.id)
        assertNull(chatGpt.writtenOffAt)
        assertEquals(BigDecimal("1830.00"), result.writtenOffAmount)
        assertEquals(listOf<Any>(SentInvoiceVoidedEvent(invoice.id!!)), events)
    }

    @Test
    fun `writing off every line only voids the invoice`() {
        val invoice = invoice(InvoiceStatus.DRAFT).apply { origin = InvoiceOrigin.SUBSCRIPTION_LEDGER }
        val only = line(invoice, "Claude", "100.00")
        `when`(invoiceRepository.findById(invoice.id!!)).thenReturn(Optional.of(invoice))
        `when`(ledgerEntryRepository.findByInvoice(invoice)).thenReturn(listOf(only))

        val result = service.writeOffLines(invoice.id!!, setOf(only.id!!), "Not his", UUID.randomUUID())

        assertNull(result.reissuedInvoice)
        assertEquals(InvoiceStatus.VOID, invoice.status)
        assertEquals(invoice, only.invoice)
        assertEquals(emptyList(), events)
    }

    @Test
    fun `lines of a paid invoice or of another invoice cannot be written off`() {
        val paid = invoice(InvoiceStatus.PAID).apply { origin = InvoiceOrigin.SUBSCRIPTION_LEDGER }
        val sent = invoice(InvoiceStatus.SENT).apply { origin = InvoiceOrigin.SUBSCRIPTION_LEDGER }
        val line = line(sent, "Claude", "100.00")
        listOf(paid, sent).forEach { `when`(invoiceRepository.findById(it.id!!)).thenReturn(Optional.of(it)) }
        `when`(ledgerEntryRepository.findByInvoice(sent)).thenReturn(listOf(line))

        assertFailsWith<IllegalStateException> {
            service.writeOffLines(paid.id!!, setOf(line.id!!), "x", UUID.randomUUID())
        }
        assertFailsWith<IllegalArgumentException> {
            service.writeOffLines(sent.id!!, setOf(UUID.randomUUID()), "x", UUID.randomUUID())
        }
        assertEquals(InvoiceStatus.SENT, sent.status)
        assertNull(line.writtenOffAt)
    }

    private fun line(invoice: Invoice, serviceName: String, amount: String) = LedgerEntry().apply {
        id = UUID.randomUUID()
        this.invoice = invoice
        subscriber = this@InvoiceServiceStatusTest.subscriber
        subscriptionService = SubscriptionService().apply { name = serviceName }
        recordedMonth = YearMonth.of(2026, 11)
        this.amount = BigDecimal(amount)
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
