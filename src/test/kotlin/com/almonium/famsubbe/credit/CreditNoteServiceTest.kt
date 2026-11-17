package com.almonium.famsubbe.credit

import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.invoice.Invoice
import com.almonium.famsubbe.invoice.InvoiceGenerationRunRepository
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.invoice.InvoiceService
import com.almonium.famsubbe.invoice.InvoiceStatus
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CreditNoteServiceTest {
    private val creditNoteRepository = mock(CreditNoteRepository::class.java)
    private val subscriberRepository = mock(SubscriberRepository::class.java)
    private val invoiceRepository = mock(InvoiceRepository::class.java)
    private val published = mutableListOf<Any>()
    private val invoiceService = InvoiceService(
        mock(InvoiceGenerationRunRepository::class.java),
        invoiceRepository,
        mock(LedgerEntryRepository::class.java),
        subscriberRepository
    )
    private val service = CreditNoteService(
        creditNoteRepository,
        subscriberRepository,
        invoiceRepository,
        invoiceService
    ) { published += it }

    private val admin = UUID.randomUUID()
    private val subscriber = Subscriber().apply {
        id = UUID.randomUUID()
        name = "Поліна"
        email = "polina@example.com"
        balance = BigDecimal.ZERO
    }
    private val notes = mutableMapOf<UUID, CreditNote>()

    init {
        `when`(subscriberRepository.findById(subscriber.id!!)).thenReturn(Optional.of(subscriber))
        `when`(subscriberRepository.save(any(Subscriber::class.java))).thenAnswer { it.getArgument<Subscriber>(0) }
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer { it.getArgument<Invoice>(0) }
        `when`(creditNoteRepository.save(any(CreditNote::class.java))).thenAnswer {
            it.getArgument<CreditNote>(0).also { note ->
                if (note.id == null) note.id = UUID.randomUUID()
                notes[note.id!!] = note
            }
        }
    }

    @Test
    fun `a credit note credits the balance, settles what it covers and records why`() {
        val invoice = invoice("150.00")
        unpaid(invoice)

        val result = service.create(request("200.00"), admin)

        assertEquals(listOf(invoice.id), result.settledInvoiceIds)
        assertEquals(InvoiceStatus.PAID, invoice.status)
        assertEquals(result.creditNote.id, invoice.paidByCreditNoteId)
        assertEquals(BigDecimal("50.00"), subscriber.balance)
        assertEquals("Paid for the admin's dinner", result.creditNote.reason)
        assertEquals(1, result.creditNote.settledInvoiceCount)
        assertEquals(listOf<Any>(CreditNoteCreatedEvent(result.creditNote.id)), published)
    }

    @Test
    fun `undo reopens what it paid and takes the credit back`() {
        val invoice = invoice("150.00")
        unpaid(invoice)
        val created = service.create(request("200.00"), admin).creditNote
        `when`(creditNoteRepository.findById(created.id)).thenReturn(Optional.of(notes.getValue(created.id)))
        `when`(invoiceRepository.findById(invoice.id!!)).thenReturn(Optional.of(invoice))
        `when`(invoiceRepository.findByPaidByCreditNoteId(created.id)).thenReturn(listOf(invoice))

        val undone = service.undo(created.id, admin)

        assertEquals(listOf(invoice.id), undone.reopenedInvoiceIds)
        assertEquals(InvoiceStatus.SENT, invoice.status)
        assertNull(invoice.paidByCreditNoteId)
        assertEquals(BigDecimal("0.00"), subscriber.balance)
        assertNotNull(undone.creditNote.undoneAt)
        assertThrows<IllegalStateException> { service.undo(created.id, admin) }
    }

    @Test
    fun `undo refuses when the credit has since paid something else`() {
        unpaid()
        val created = service.create(request("200.00"), admin).creditNote
        `when`(creditNoteRepository.findById(created.id)).thenReturn(Optional.of(notes.getValue(created.id)))
        subscriber.balance = BigDecimal("20.00")

        assertThrows<IllegalStateException> { service.undo(created.id, admin) }
        assertEquals(BigDecimal("20.00"), subscriber.balance)
    }

    @Test
    fun `billing exempt subscribers and future dates are refused`() {
        assertThrows<IllegalArgumentException> {
            service.create(request("10.00").copy(occurredOn = LocalDate.now().plusDays(3)), admin)
        }
        subscriber.billingExempt = true
        assertThrows<IllegalStateException> { service.create(request("10.00"), admin) }
    }

    private fun request(amount: String) = CreditNoteCreateRequest(
        subscriberId = subscriber.id,
        amount = BigDecimal(amount),
        occurredOn = LocalDate.of(2026, 11, 14),
        reason = "  Paid for the admin's dinner ",
    )

    private fun unpaid(vararg invoices: Invoice) {
        `when`(invoiceRepository.findBySubscriberAndStatusIn(subscriber, listOf(InvoiceStatus.DRAFT, InvoiceStatus.SENT)))
            .thenReturn(invoices.toList())
    }

    private fun invoice(total: String) = Invoice().apply {
        id = UUID.randomUUID()
        subscriber = this@CreditNoteServiceTest.subscriber
        fromMonth = YearMonth.of(2026, 10)
        toMonth = YearMonth.of(2026, 10)
        invoiceDate = LocalDate.of(2026, 11, 1)
        totalAmount = BigDecimal(total)
        status = InvoiceStatus.SENT
        sentAt = Instant.now()
        createdAt = Instant.now()
        statusChangedAt = Instant.now()
        createdByAccountId = UUID.randomUUID()
    }
}
