package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import com.almonium.famsubbe.subscription.MembershipRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class InvoiceServiceManualInvoiceTest {
    private val invoiceGenerationRunRepository = mock(InvoiceGenerationRunRepository::class.java)
    private val invoiceRepository = mock(InvoiceRepository::class.java)
    private val ledgerEntryRepository = mock(LedgerEntryRepository::class.java)
    private val subscriberRepository = mock(SubscriberRepository::class.java)
    private val membershipRepository = mock(MembershipRepository::class.java)
    private val invoiceEmailService = mock(InvoiceEmailService::class.java)
    private val service = InvoiceService(
        invoiceGenerationRunRepository,
        invoiceRepository,
        ledgerEntryRepository,
        subscriberRepository,
        membershipRepository,
        invoiceEmailService,
        false
    )

    @Test
    fun `manual invoice preserves the explicitly supplied invoice date`() {
        val subscriberId = UUID.randomUUID()
        val subscriber = Subscriber().apply {
            id = subscriberId
            name = "Alice"
            email = "alice@example.com"
            balance = BigDecimal.ZERO
        }
        val selectedDate = LocalDate.of(2025, 12, 17)

        `when`(subscriberRepository.findById(subscriberId)).thenReturn(Optional.of(subscriber))
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer {
            it.getArgument<Invoice>(0).apply { id = UUID.randomUUID() }
        }

        val result = service.createManualInvoice(
            ManualInvoiceCreateRequest(
                subscriberId = subscriberId,
                amount = BigDecimal("450.00"),
                invoiceDate = selectedDate,
                notes = "Historical adjustment"
            ),
            UUID.randomUUID()
        )

        assertEquals(selectedDate, result.invoiceDate)
        assertEquals(YearMonth.from(selectedDate), result.fromMonth)
        assertEquals(YearMonth.from(selectedDate), result.toMonth)
    }
}
