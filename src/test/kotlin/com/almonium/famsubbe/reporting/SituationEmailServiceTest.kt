package com.almonium.famsubbe.reporting

import com.almonium.famsubbe.accounting.ChargeRepository
import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.invoice.FakeInvoiceEmailService
import com.almonium.famsubbe.invoice.Invoice
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.invoice.InvoiceStatus
import com.almonium.famsubbe.payment.PaymentDetails
import com.almonium.famsubbe.payment.PaymentDetailsService
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import com.almonium.famsubbe.subscriber.SubscriberStandingService
import com.almonium.famsubbe.subscription.MembershipRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class SituationEmailServiceTest {
    private val invoiceRepository = mock(InvoiceRepository::class.java)
    private val subscriberRepository = mock(SubscriberRepository::class.java)
    private val paymentDetailsService = mock(PaymentDetailsService::class.java)
    private val emailService = FakeInvoiceEmailService()

    private val subscriber = Subscriber().apply {
        id = UUID.randomUUID()
        name = "Alice"
        email = "alice@example.com"
        payToken = "token"
        balance = BigDecimal.ZERO
    }
    private val draft = invoice(InvoiceStatus.DRAFT)
    private val sent = invoice(InvoiceStatus.SENT)

    init {
        `when`(subscriberRepository.findById(subscriber.id!!)).thenReturn(Optional.of(subscriber))
        `when`(invoiceRepository.findBySubscriberAndStatusIn(subscriber, SubscriberStandingService.UNPAID_STATUSES))
            .thenReturn(listOf(draft, sent))
        `when`(invoiceRepository.findAllById(listOf(draft.id!!))).thenReturn(listOf(draft))
        `when`(invoiceRepository.saveAll(any<Iterable<Invoice>>())).thenAnswer { it.getArgument<Iterable<Invoice>>(0).toList() }
        `when`(paymentDetailsService.forSubscriber(subscriber, BigDecimal("300.00")))
            .thenReturn(PaymentDetails("Recipient", "UA00", "0000", "FamSub FS-TEST", "https://pay", null))
    }

    @Test
    fun `the drafts a situation email lists count as sent`() {
        val result = service(dryRun = false).sendOnDemand(subscriber.id!!)

        assertEquals(listOf(draft.id), result.markedSentInvoiceIds)
        assertEquals(InvoiceStatus.SENT, draft.status)
        assertEquals(true, draft.emailSent)
    }

    @Test
    fun `a dry run leaves the drafts as they are`() {
        val result = service(dryRun = true).sendOnDemand(subscriber.id!!)

        assertEquals(emptyList(), result.markedSentInvoiceIds)
        assertEquals(InvoiceStatus.DRAFT, draft.status)
    }

    private fun service(dryRun: Boolean) = SituationEmailService(
        invoiceRepository,
        mock(LedgerEntryRepository::class.java),
        SubscriberStandingService(
            subscriberRepository,
            invoiceRepository,
            mock(MembershipRepository::class.java),
            mock(ChargeRepository::class.java)
        ),
        emailService,
        subscriberRepository,
        paymentDetailsService,
        TransactionTemplate(mock(PlatformTransactionManager::class.java).also {
            `when`(it.getTransaction(any())).thenReturn(SimpleTransactionStatus())
        }),
        dryRun,
        BigDecimal("200")
    )

    private fun invoice(status: InvoiceStatus) = Invoice().apply {
        id = UUID.randomUUID()
        subscriber = this@SituationEmailServiceTest.subscriber
        fromMonth = YearMonth.of(2026, 9)
        toMonth = YearMonth.of(2026, 9)
        invoiceDate = LocalDate.of(2026, 10, 1)
        createdAt = Instant.parse("2026-10-01T10:00:00Z")
        totalAmount = BigDecimal("150.00")
        this.status = status
    }
}
