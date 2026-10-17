package com.almonium.famsubbe.subscriber

import com.almonium.famsubbe.accounting.CostCalculationService
import com.almonium.famsubbe.invoice.Invoice
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.invoice.InvoiceStatus
import com.almonium.famsubbe.subscription.MembershipRepository
import com.almonium.famsubbe.util.BillingTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.YearMonth
import java.util.*

/**
 * Where a subscriber stands right now: what they owe, what credit they hold,
 * and which subscriptions they are on. The admin details view, the cabinet,
 * the situation emails, and invoice emails all read it from here so the
 * "amount owed" figure cannot drift between them.
 */
@Service
@Transactional(readOnly = true)
class SubscriberStandingService(
    private val subscriberRepository: SubscriberRepository,
    private val invoiceRepository: InvoiceRepository,
    private val membershipRepository: MembershipRepository
) {
    fun getDetails(subscriberId: UUID): SubscriberDetailResponse {
        val subscriber = subscriberRepository.findById(subscriberId)
            .orElseThrow { NoSuchElementException("Subscriber not found with id: $subscriberId") }
        val unpaidInvoices = unpaidInvoices(subscriber)
            .sortedWith(compareByDescending<Invoice> { it.invoiceDate }.thenByDescending { it.createdAt })

        return SubscriberDetailResponse(
            id = requireNotNull(subscriber.id),
            name = subscriber.name.orEmpty(),
            email = subscriber.email.orEmpty(),
            payToken = requireNotNull(subscriber.payToken),
            balance = subscriber.balance ?: BigDecimal.ZERO,
            totalAmountOwed = amountOwed(unpaidInvoices, subscriber),
            activeSubscriptions = activeSubscriptions(subscriberId),
            unpaidInvoices = unpaidInvoices.map { it.toUnpaidInvoiceDto() }
        )
    }

    /** Unpaid invoices minus prepaid credit; negative when the credit exceeds the debt. */
    fun amountOwed(subscriber: Subscriber): BigDecimal =
        amountOwed(unpaidInvoices(subscriber), subscriber)

    fun activeSubscriptions(subscriberId: UUID): List<ActiveSubscriptionDto> {
        val month = BillingTime.currentMonth()
        return membershipRepository.findActiveBySubscriberAndMonth(subscriberId, month)
            .map { membership ->
                val service = membership.subscriptionService
                val price = service?.price ?: BigDecimal.ZERO
                ActiveSubscriptionDto(
                    id = requireNotNull(membership.id),
                    serviceName = service?.name.orEmpty(),
                    servicePrice = price,
                    startMonth = membership.startMonth?.toString().orEmpty(),
                    endMonth = membership.endMonth?.toString(),
                    monthlyShare = monthlyShare(subscriberId, requireNotNull(service?.id), price, month)
                )
            }
    }

    /** Ordered and split exactly as [CostCalculationService] splits a charge, so the estimate matches the bill. */
    private fun monthlyShare(subscriberId: UUID, serviceId: UUID, price: BigDecimal, month: YearMonth): BigDecimal {
        val members = membershipRepository.findActiveByServiceAndMonth(serviceId, month)
            .distinctBy { requireNotNull(it.subscriber).id }
            .sortedBy { requireNotNull(it.subscriber).id.toString() }
        val index = members.indexOfFirst { requireNotNull(it.subscriber).id == subscriberId }
        return CostCalculationService.splitByWeight(price, members.map { it.shareWeight })[index]
    }

    fun unpaidInvoices(subscriber: Subscriber): List<Invoice> =
        invoiceRepository.findBySubscriberAndStatusIn(subscriber, UNPAID_STATUSES)

    private fun Invoice.toUnpaidInvoiceDto() = UnpaidInvoiceDto(
        id = requireNotNull(id),
        totalAmount = totalAmount ?: BigDecimal.ZERO,
        fromMonth = fromMonth?.toString().orEmpty(),
        toMonth = toMonth?.toString().orEmpty(),
        invoiceDate = requireNotNull(invoiceDate),
        createdAt = requireNotNull(createdAt),
        status = status.name,
        notes = notes,
        origin = origin.name
    )

    companion object {
        val UNPAID_STATUSES = listOf(InvoiceStatus.DRAFT, InvoiceStatus.SENT)

        fun amountOwed(unpaidInvoices: Collection<Invoice>, subscriber: Subscriber): BigDecimal =
            unpaidInvoices.sumOf { it.totalAmount ?: BigDecimal.ZERO } - (subscriber.balance ?: BigDecimal.ZERO)
    }
}
