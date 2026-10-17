package com.almonium.famsubbe.subscriber

import com.almonium.famsubbe.accounting.Charge
import com.almonium.famsubbe.accounting.ChargeRepository
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.subscription.Membership
import com.almonium.famsubbe.subscription.MembershipRepository
import com.almonium.famsubbe.subscription.SubscriptionService
import com.almonium.famsubbe.util.BillingTime
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals

class SubscriberMonthlyShareTest {
    private val membershipRepository = mock(MembershipRepository::class.java)
    private val chargeRepository = mock(ChargeRepository::class.java)
    private val service = SubscriberStandingService(
        mock(SubscriberRepository::class.java),
        mock(InvoiceRepository::class.java),
        membershipRepository,
        chargeRepository
    )
    private val netflix = SubscriptionService().apply { id = UUID.randomUUID(); name = "Netflix"; price = BigDecimal("9.99"); currency = "EUR" }

    @Test
    fun `the monthly share follows this month's weights`() {
        val month = BillingTime.currentMonth()
        val polina = membership(weight = 2)
        val others = listOf(membership(weight = 2), membership(weight = 1), membership(weight = 1))
        val polinaId = requireNotNull(polina.subscriber?.id)
        `when`(membershipRepository.findActiveBySubscriberAndMonth(polinaId, month)).thenReturn(listOf(polina))
        `when`(membershipRepository.findActiveByServiceAndMonth(requireNotNull(netflix.id), month)).thenReturn(others + polina)
        // The price is in euros; the share is of the latest bill, in hryvnias.
        `when`(chargeRepository.findFirstBySubscriptionServiceIdOrderByChargeMonthDescCreatedAtDesc(requireNotNull(netflix.id)))
            .thenReturn(Charge().apply { amount = BigDecimal("549.00") })

        val share = service.activeSubscriptions(polinaId).single().monthlyShare

        // Weights 2, 2, 1, 1: 549.00 × 2/6.
        assertEquals(BigDecimal("183.00"), share)
    }

    @Test
    fun `a service never charged in a foreign currency has no hryvnia estimate yet`() {
        val month = BillingTime.currentMonth()
        val polina = membership(weight = 1)
        val polinaId = requireNotNull(polina.subscriber?.id)
        `when`(membershipRepository.findActiveBySubscriberAndMonth(polinaId, month)).thenReturn(listOf(polina))
        `when`(membershipRepository.findActiveByServiceAndMonth(requireNotNull(netflix.id), month)).thenReturn(listOf(polina))

        assertEquals(BigDecimal.ZERO, service.activeSubscriptions(polinaId).single().servicePrice)
    }

    private fun membership(weight: Int) = Membership().apply {
        id = UUID.randomUUID()
        subscriptionService = netflix
        subscriber = Subscriber().apply { id = UUID.randomUUID() }
        startMonth = YearMonth.of(2025, 1)
        shareWeight = weight
    }
}
