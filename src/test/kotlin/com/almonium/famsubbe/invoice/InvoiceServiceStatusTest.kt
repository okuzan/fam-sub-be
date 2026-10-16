package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.accounting.LedgerEntry
import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.reporting.WeeklySituationInvoiceDto
import com.almonium.famsubbe.subscriber.ActiveSubscriptionDto
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import com.almonium.famsubbe.subscription.MembershipRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvoiceServiceStatusTest {
    private val invoiceRepository = mock(InvoiceRepository::class.java)
    private val ledgerEntryRepository = mock(LedgerEntryRepository::class.java)
    private val subscriberRepository = mock(SubscriberRepository::class.java)
    private val invoiceEmailService = FakeInvoiceEmailService()
    private val service = InvoiceService(
        mock(InvoiceGenerationRunRepository::class.java),
        invoiceRepository,
        ledgerEntryRepository,
        subscriberRepository,
        mock(MembershipRepository::class.java),
        invoiceEmailService,
        false
    )

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

        val result = service.updateStatus(invoice.id!!, InvoiceStatus.VOID)

        assertEquals(InvoiceStatus.VOID.name, result.status)
        assertNull(entry.invoice)
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
    fun `manual invoice stays DRAFT when its email fails`() {
        `when`(subscriberRepository.findById(subscriber.id!!)).thenReturn(Optional.of(subscriber))
        invoiceEmailService.succeeds = false

        val result = service.createManualInvoice(manualRequest(), UUID.randomUUID())

        assertEquals(InvoiceStatus.DRAFT.name, result.status)
        assertFalse(result.emailSent)
        assertNull(result.sentAt)
    }

    @Test
    fun `manual invoice becomes SENT once its email goes out`() {
        `when`(subscriberRepository.findById(subscriber.id!!)).thenReturn(Optional.of(subscriber))
        invoiceEmailService.succeeds = true

        val result = service.createManualInvoice(manualRequest(), UUID.randomUUID())

        assertEquals(InvoiceStatus.SENT.name, result.status)
        assertTrue(result.emailSent)
    }

    private fun manualRequest() = ManualInvoiceCreateRequest(
        subscriberId = subscriber.id!!,
        amount = BigDecimal("100.00"),
        invoiceDate = LocalDate.of(2026, 10, 1),
        notes = "Adjustment",
        sendEmail = true
    )

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

private class FakeInvoiceEmailService : InvoiceEmailService {
    var succeeds = true

    override fun sendInvoiceEmail(invoice: Invoice, entries: List<LedgerEntry>, totalAmountOwed: BigDecimal) = succeeds

    override fun sendSituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>
    ) = succeeds

    override fun sendWeeklySituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>
    ) = succeeds

    override fun sendDebtPaidEmail(
        toEmail: String,
        subscriberName: String,
        paidInvoicesCount: Int,
        totalPaidAmount: BigDecimal,
        balanceAfter: BigDecimal,
        creditWrittenOff: BigDecimal
    ) = succeeds
}
