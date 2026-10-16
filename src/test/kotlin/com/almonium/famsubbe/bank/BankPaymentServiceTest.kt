package com.almonium.famsubbe.bank

import com.almonium.famsubbe.invoice.IncomingPaymentResult
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.invoice.InvoiceService
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import com.almonium.famsubbe.subscriber.SubscriberStandingService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

class BankPaymentServiceTest {
    private val transactionRepository = mock(BankTransactionRepository::class.java)
    private val senderRepository = mock(BankSenderRepository::class.java)
    private val invoiceService = mock(InvoiceService::class.java)
    private val standingService = mock(SubscriberStandingService::class.java)
    private val subscriberRepository = mock(SubscriberRepository::class.java)
    private val service = BankPaymentService(
        transactionRepository,
        senderRepository,
        subscriberRepository,
        mock(InvoiceRepository::class.java),
        invoiceService,
        standingService,
        MonoProperties(token = "t", accountId = "acc", autoApplyTolerance = BigDecimal("20.00"))
    )

    private val subscriber = Subscriber().apply {
        id = UUID.randomUUID()
        name = "Поліна"
        balance = BigDecimal.ZERO
    }

    init {
        `when`(transactionRepository.saveAndFlush(any(BankTransaction::class.java)))
            .thenAnswer { it.getArgument<BankTransaction>(0) }
        `when`(invoiceService.applyIncomingPayment(subscriber, BigDecimal("31.93")))
            .thenReturn(IncomingPaymentResult(listOf(UUID.randomUUID()), BigDecimal("31.93"), BigDecimal.ZERO))
    }

    @Test
    fun `a payment from a remembered sender within what they owe applies on its own`() {
        rememberSender()
        `when`(standingService.amountOwed(subscriber)).thenReturn(BigDecimal("31.93"))

        val outcome = service.importMonoItem(item(3193))

        assertEquals(BankImportOutcome.APPLIED, outcome)
        verify(invoiceService).applyIncomingPayment(subscriber, BigDecimal("31.93"))
    }

    @Test
    fun `a payment well above what a remembered sender owes waits for review`() {
        rememberSender()
        `when`(standingService.amountOwed(subscriber)).thenReturn(BigDecimal("31.93"))

        val outcome = service.importMonoItem(item(500000))

        assertEquals(BankImportOutcome.NEEDS_REVIEW, outcome)
        verify(invoiceService, never()).applyIncomingPayment(subscriber, BigDecimal("5000.00"))
    }

    @Test
    fun `a payment whose sender holds a subscriber's name and surname applies on its own`() {
        withSurnames()
        `when`(standingService.amountOwed(subscriber)).thenReturn(BigDecimal("31.93"))

        val outcome = service.importMonoItem(item(3193, description = "Від: ЧЕРНИШОВА Поліна Олегівна"))

        assertEquals(BankImportOutcome.APPLIED, outcome)
        verify(invoiceService).applyIncomingPayment(subscriber, BigDecimal("31.93"))
    }

    @Test
    fun `a surname alone does not match, so a sister's transfer is not taken for hers`() {
        withSurnames()

        val outcome = service.importMonoItem(item(3193, description = "Від: Олена Чернишова"))

        assertEquals(BankImportOutcome.NEEDS_REVIEW, outcome)
        verify(invoiceService, never()).applyIncomingPayment(subscriber, BigDecimal("31.93"))
    }

    @Test
    fun `a name match well above what they owe waits for review`() {
        withSurnames()
        `when`(standingService.amountOwed(subscriber)).thenReturn(BigDecimal("31.93"))

        val outcome = service.importMonoItem(item(500000))

        assertEquals(BankImportOutcome.NEEDS_REVIEW, outcome)
    }

    @Test
    fun `a name match wins over a remembered sender pointing elsewhere`() {
        withSurnames()
        val other = Subscriber().apply { id = UUID.randomUUID(); name = "Софія" }
        `when`(senderRepository.findBySenderKey("name:поліна чернишова"))
            .thenReturn(BankSender().apply { subscriber = other })
        `when`(standingService.amountOwed(subscriber)).thenReturn(BigDecimal("31.93"))

        service.importMonoItem(item(3193))

        verify(invoiceService).applyIncomingPayment(subscriber, BigDecimal("31.93"))
    }

    @Test
    fun `a payment carrying a payment code applies to its subscriber and teaches the sender`() {
        `when`(subscriberRepository.findByPaymentCode("7K2Q9X")).thenReturn(subscriber)
        `when`(invoiceService.applyIncomingPayment(subscriber, BigDecimal("5000.00")))
            .thenReturn(IncomingPaymentResult(emptyList(), BigDecimal.ZERO, BigDecimal("5000.00")))

        val outcome = service.importMonoItem(item(500000).copy(comment = "FamSub FS-7K2Q9X"))

        assertEquals(BankImportOutcome.APPLIED, outcome)
        verify(senderRepository).save(any(BankSender::class.java))
    }

    @Test
    fun `a coded payment counts even when the bank names no sender`() {
        `when`(subscriberRepository.findByPaymentCode("7K2Q9X")).thenReturn(subscriber)

        val outcome = service.importMonoItem(item(3193, description = "FamSub FS-7K2Q9X"))

        assertEquals(BankImportOutcome.APPLIED, outcome)
    }

    @Test
    fun `a payment from an unknown sender waits for review`() {
        val outcome = service.importMonoItem(item(3193))

        assertEquals(BankImportOutcome.NEEDS_REVIEW, outcome)
    }

    @Test
    fun `a payment from an ignored sender is filed as ignored`() {
        `when`(senderRepository.findBySenderKey(anyString())).thenReturn(BankSender().apply { ignored = true })

        val outcome = service.importMonoItem(item(3193))

        assertEquals(BankImportOutcome.IGNORED, outcome)
    }

    @Test
    fun `an operation already imported is skipped`() {
        `when`(transactionRepository.existsByProviderAndExternalId(BankProvider.MONO, "op-1")).thenReturn(true)

        assertEquals(BankImportOutcome.DUPLICATE, service.importMonoItem(item(3193)))
    }

    @Test
    fun `an own-card top-up is not a payment`() {
        val outcome = service.importMonoItem(item(3193, description = "З Білої картки"))

        assertEquals(BankImportOutcome.NOT_A_PAYMENT, outcome)
    }

    private fun withSurnames() {
        subscriber.surname = "Чернишова"
        val sister = Subscriber().apply { id = UUID.randomUUID(); name = "Софія"; surname = "Чернишова" }
        `when`(subscriberRepository.findBySurnameIsNotNull()).thenReturn(listOf(subscriber, sister))
    }

    private fun rememberSender() {
        `when`(senderRepository.findBySenderKey("name:поліна чернишова")).thenReturn(
            BankSender().apply {
                senderKey = "name:поліна чернишова"
                subscriber = this@BankPaymentServiceTest.subscriber
            }
        )
    }

    private fun item(amount: Long, description: String = "Від: Поліна Чернишова") = MonoStatementItem(
        id = "op-1",
        time = 1_760_000_000,
        amount = amount,
        description = description
    )
}
