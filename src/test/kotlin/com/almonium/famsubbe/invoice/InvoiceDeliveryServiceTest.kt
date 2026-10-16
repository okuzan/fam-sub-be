package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.accounting.LedgerEntryRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvoiceDeliveryServiceTest {
    private val invoiceRepository = mock(InvoiceRepository::class.java)
    private val ledgerEntryRepository = mock(LedgerEntryRepository::class.java)
    private val subscriberRepository = mock(SubscriberRepository::class.java)
    private val emailService = FakeInvoiceEmailService()
    private val invoiceService = InvoiceService(
        mock(InvoiceGenerationRunRepository::class.java),
        invoiceRepository,
        ledgerEntryRepository,
        subscriberRepository
    )
    private val delivery = InvoiceDeliveryService(
        invoiceService,
        invoiceRepository,
        ledgerEntryRepository,
        SubscriberStandingService(subscriberRepository, invoiceRepository, mock(MembershipRepository::class.java)),
        emailService,
        TransactionTemplate(mock(PlatformTransactionManager::class.java).also {
            `when`(it.getTransaction(any())).thenReturn(SimpleTransactionStatus())
        }),
        false
    )

    private val subscriber = Subscriber().apply {
        id = UUID.randomUUID()
        name = "Alice"
        email = "alice@example.com"
        balance = BigDecimal.ZERO
    }

    init {
        `when`(subscriberRepository.findById(subscriber.id!!)).thenReturn(Optional.of(subscriber))
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer { call ->
            call.getArgument<Invoice>(0).apply {
                if (id == null) id = UUID.randomUUID()
                `when`(invoiceRepository.findById(id!!)).thenReturn(Optional.of(this))
            }
        }
    }

    @Test
    fun `manual invoice stays DRAFT when its email fails`() {
        emailService.succeeds = false

        val result = delivery.createManual(manualRequest(), UUID.randomUUID())

        assertEquals(InvoiceStatus.DRAFT.name, result.status)
        assertFalse(result.emailSent)
        assertNull(result.sentAt)
    }

    @Test
    fun `manual invoice becomes SENT once its email goes out`() {
        emailService.succeeds = true

        val result = delivery.createManual(manualRequest(), UUID.randomUUID())

        assertEquals(InvoiceStatus.SENT.name, result.status)
        assertTrue(result.emailSent)
    }

    @Test
    fun `one failing draft does not stop the others from being marked SENT`() {
        val good = draft()
        val broken = draft()
        `when`(invoiceRepository.findByStatusInWithSubscriber(listOf(InvoiceStatus.DRAFT)))
            .thenReturn(listOf(broken, good))
        `when`(invoiceRepository.findById(broken.id!!)).thenThrow(IllegalStateException("database hiccup"))
        `when`(invoiceRepository.findById(good.id!!)).thenReturn(Optional.of(good))

        val result = delivery.sendDrafts()

        assertEquals(2, result.attemptedCount)
        assertEquals(1, result.sentCount)
        assertEquals(1, result.failedCount)
        assertEquals(InvoiceStatus.SENT, good.status)
        assertEquals(InvoiceStatus.DRAFT, broken.status)
    }

    private fun manualRequest() = ManualInvoiceCreateRequest(
        subscriberId = subscriber.id!!,
        amount = BigDecimal("100.00"),
        invoiceDate = LocalDate.of(2026, 10, 1),
        notes = "Adjustment",
        sendEmail = true
    )

    private fun draft() = Invoice().apply {
        id = UUID.randomUUID()
        subscriber = this@InvoiceDeliveryServiceTest.subscriber
        fromMonth = YearMonth.of(2026, 9)
        toMonth = YearMonth.of(2026, 9)
        invoiceDate = LocalDate.of(2026, 10, 1)
        totalAmount = BigDecimal("100.00")
        status = InvoiceStatus.DRAFT
        createdAt = Instant.now()
        statusChangedAt = Instant.now()
        createdByAccountId = UUID.randomUUID()
    }
}
