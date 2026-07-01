package com.almonium.famsubbe.subscriber

import com.almonium.famsubbe.invoice.InvoiceLedgerEntryResponse
import com.almonium.famsubbe.invoice.Invoice
import com.almonium.famsubbe.invoice.InvoiceStatus
import com.almonium.famsubbe.entity.LedgerEntry
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.repository.LedgerEntryRepository
import com.almonium.famsubbe.subscription.MembershipRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.YearMonth
import java.util.*

@Service
@Transactional(readOnly = true)
class SubscriberCabinetService(
    private val subscriberRepository: SubscriberRepository,
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val membershipRepository: MembershipRepository
) {

    fun getCabinet(email: String): SubscriberCabinetResponse {
        val subscriber = findSubscriberByEmail(email)
        val subscriberId = requireNotNull(subscriber.id)
        val unpaidInvoices = findInvoices(subscriber, listOf(InvoiceStatus.DRAFT, InvoiceStatus.SENT))

        val totalUnpaidAmount = unpaidInvoices
            .map { it.totalAmount ?: BigDecimal.ZERO }
            .fold(BigDecimal.ZERO, BigDecimal::add)

        return SubscriberCabinetResponse(
            subscriber = subscriber.toProfileResponse(),
            balance = subscriber.balance ?: BigDecimal.ZERO,
            totalAmountOwed = totalUnpaidAmount - (subscriber.balance ?: BigDecimal.ZERO),
            activeSubscriptions = findActiveSubscriptions(subscriberId),
            unpaidInvoices = unpaidInvoices.map { it.toSubscriberSummaryResponse() }
        )
    }

    fun getInvoices(email: String): List<SubscriberInvoiceSummaryResponse> {
        val subscriber = findSubscriberByEmail(email)
        return invoiceRepository.findAllFiltered(requireNotNull(subscriber.id))
            .map { it.toSubscriberSummaryResponse() }
    }

    fun getInvoice(email: String, invoiceId: UUID): SubscriberInvoiceDetailResponse {
        val subscriber = findSubscriberByEmail(email)
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found") }

        if (invoice.subscriber?.id != subscriber.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found")
        }

        return SubscriberInvoiceDetailResponse(
            invoice = invoice.toSubscriberSummaryResponse(),
            entries = ledgerEntryRepository.findByInvoiceId(invoiceId)
                .map { it.toInvoiceLedgerEntryResponse() }
        )
    }

    private fun findSubscriberByEmail(email: String): Subscriber =
        subscriberRepository.findByEmailIgnoreCase(email.trim().lowercase())
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriber not found")

    private fun findInvoices(subscriber: Subscriber, statuses: Collection<InvoiceStatus>): List<Invoice> =
        invoiceRepository.findBySubscriberAndStatusIn(subscriber, statuses)
            .sortedByDescending { it.createdAt }

    private fun findActiveSubscriptions(subscriberId: UUID): List<ActiveSubscriptionDto> {
        val currentMonth = YearMonth.now()
        return membershipRepository.findActiveBySubscriberAndMonth(subscriberId, currentMonth)
            .map { membership ->
                ActiveSubscriptionDto(
                    id = requireNotNull(membership.id),
                    serviceName = membership.subscriptionService?.name ?: "",
                    servicePrice = membership.subscriptionService?.price ?: BigDecimal.ZERO,
                    startMonth = membership.startMonth?.toString() ?: "",
                    endMonth = membership.endMonth?.toString()
                )
            }
    }

    private fun Subscriber.toProfileResponse(): SubscriberCabinetProfileResponse =
        SubscriberCabinetProfileResponse(
            id = requireNotNull(id),
            name = name ?: "",
            email = email ?: ""
        )

    private fun Invoice.toSubscriberSummaryResponse(): SubscriberInvoiceSummaryResponse =
        SubscriberInvoiceSummaryResponse(
            id = requireNotNull(id),
            fromMonth = requireNotNull(fromMonth),
            toMonth = requireNotNull(toMonth),
            totalAmount = totalAmount ?: BigDecimal.ZERO,
            status = status.name,
            createdAt = requireNotNull(createdAt),
            sentAt = sentAt,
            notes = notes,
            origin = origin.name
        )

    private fun LedgerEntry.toInvoiceLedgerEntryResponse(): InvoiceLedgerEntryResponse =
        InvoiceLedgerEntryResponse(
            ledgerEntryId = requireNotNull(id),
            chargeId = requireNotNull(charge?.id),
            subscriptionServiceId = requireNotNull(subscriptionService?.id),
            subscriptionServiceName = requireNotNull(subscriptionService?.name),
            recordedMonth = requireNotNull(recordedMonth),
            amount = requireNotNull(amount),
            participantCount = participantCount,
            calculatedAt = requireNotNull(calculatedAt)
        )
}
