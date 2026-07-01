package com.almonium.famsubbe.accounting

import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscription.Membership
import com.almonium.famsubbe.subscription.MembershipRepository
import com.almonium.famsubbe.subscription.SubscriptionService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CostCalculationServiceTest {
    private val chargeRepository = mock(ChargeRepository::class.java)
    private val membershipRepository = mock(MembershipRepository::class.java)
    private val ledgerEntryRepository = mock(LedgerEntryRepository::class.java)
    private val batchRepository = mock(CostCalculationBatchRepository::class.java)
    private val service = CostCalculationService(
        chargeRepository,
        membershipRepository,
        ledgerEntryRepository,
        batchRepository
    )

    @Test
    fun `calculates a late charge and skips charges already recorded for the same month`() {
        val month = YearMonth.of(2026, 6)
        val oldCharge = charge(month, "Existing service", "90.00")
        val lateCharge = charge(month, "Forgotten service", "30.00")
        val subscriber = subscriber("Alice")
        val membership = membership(lateCharge, subscriber, month)

        `when`(chargeRepository.findByChargeMonthForUpdate(month))
            .thenReturn(listOf(oldCharge, lateCharge))
        `when`(
            ledgerEntryRepository.findRecordedChargeIds(
                listOf(requireNotNull(oldCharge.id), requireNotNull(lateCharge.id))
            )
        ).thenReturn(setOf(requireNotNull(oldCharge.id)))
        `when`(
            membershipRepository.findActiveByServiceAndMonth(
                requireNotNull(lateCharge.subscriptionService?.id),
                month
            )
        ).thenReturn(listOf(membership))
        `when`(batchRepository.save(any(CostCalculationBatch::class.java))).thenAnswer {
            it.getArgument<CostCalculationBatch>(0).apply { id = UUID.randomUUID() }
        }
        `when`(ledgerEntryRepository.saveAll(any<Iterable<LedgerEntry>>())).thenAnswer {
            it.getArgument<Iterable<LedgerEntry>>(0).toList()
        }

        val result = service.calculateAndRecordCosts(month, month, UUID.randomUUID())

        assertNotNull(result.batchId)
        assertEquals(1, result.chargesProcessed)
        assertEquals(1, result.chargesSkipped)
        assertEquals(1, result.ledgerEntriesCreated)
        assertEquals(requireNotNull(lateCharge.id), result.items.single().chargeId)
        verify(ledgerEntryRepository).flush()
        verify(
            membershipRepository,
            never()
        ).findActiveByServiceAndMonth(requireNotNull(oldCharge.subscriptionService?.id), month)
    }

    @Test
    fun `repeating a fully recorded period succeeds without creating an empty batch`() {
        val month = YearMonth.of(2026, 6)
        val charge = charge(month, "Existing service", "90.00")

        `when`(chargeRepository.findByChargeMonthForUpdate(month)).thenReturn(listOf(charge))
        `when`(ledgerEntryRepository.findRecordedChargeIds(listOf(requireNotNull(charge.id))))
            .thenReturn(setOf(requireNotNull(charge.id)))

        val result = service.calculateAndRecordCosts(month, month, UUID.randomUUID())

        assertNull(result.batchId)
        assertEquals(0, result.chargesProcessed)
        assertEquals(1, result.chargesSkipped)
        assertEquals(0, result.ledgerEntriesCreated)
        assertEquals(emptyList(), result.items)
        verifyNoInteractions(membershipRepository, batchRepository)
        verify(ledgerEntryRepository, never()).flush()
    }

    @Test
    fun `a period containing no charges is a successful no-op`() {
        val month = YearMonth.of(2026, 6)
        `when`(chargeRepository.findByChargeMonthForUpdate(month)).thenReturn(emptyList())

        val result = service.calculateAndRecordCosts(month, month, UUID.randomUUID())

        assertNull(result.batchId)
        assertEquals(0, result.chargesProcessed)
        assertEquals(0, result.chargesSkipped)
        assertEquals(0, result.ledgerEntriesCreated)
        verifyNoInteractions(membershipRepository, ledgerEntryRepository, batchRepository)
    }

    private fun charge(month: YearMonth, serviceName: String, amount: String): Charge {
        val subscriptionService = SubscriptionService().apply {
            id = UUID.randomUUID()
            name = serviceName
            price = BigDecimal(amount)
        }
        return Charge().apply {
            id = UUID.randomUUID()
            this.subscriptionService = subscriptionService
            this.amount = BigDecimal(amount)
            chargeMonth = month
        }
    }

    private fun subscriber(name: String) = Subscriber().apply {
        id = UUID.randomUUID()
        this.name = name
        email = "${name.lowercase()}@example.com"
        balance = BigDecimal.ZERO
    }

    private fun membership(charge: Charge, subscriber: Subscriber, month: YearMonth) =
        Membership().apply {
            id = UUID.randomUUID()
            subscriptionService = charge.subscriptionService
            this.subscriber = subscriber
            startMonth = month
        }
}
