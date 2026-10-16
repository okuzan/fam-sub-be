package com.almonium.famsubbe.subscriber

import com.almonium.famsubbe.invoice.InvoiceLedgerEntryResponse
import com.almonium.famsubbe.invoice.Invoice
import com.almonium.famsubbe.accounting.LedgerEntry
import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.invoice.InvoiceRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.*

@Service
@Transactional(readOnly = true)
class SubscriberCabinetService(
    private val subscriberRepository: SubscriberRepository,
    private val invoiceRepository: InvoiceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val standingService: SubscriberStandingService
) {

    fun getCabinet(email: String): SubscriberCabinetResponse {
        val subscriber = findSubscriberByEmail(email)
        val subscriberId = requireNotNull(subscriber.id)
        val unpaidInvoices = standingService.unpaidInvoices(subscriber)
            .sortedByDescending { it.createdAt }

        return SubscriberCabinetResponse(
            subscriber = subscriber.toProfileResponse(),
            payToken = requireNotNull(subscriber.payToken),
            balance = subscriber.balance ?: BigDecimal.ZERO,
            totalAmountOwed = SubscriberStandingService.amountOwed(unpaidInvoices, subscriber),
            activeSubscriptions = standingService.activeSubscriptions(subscriberId),
            unpaidInvoices = unpaidInvoices.map { it.toSubscriberSummaryResponse() }
        )
    }

    fun getSubscriberId(email: String): UUID = requireNotNull(findSubscriberByEmail(email).id)

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
            invoiceDate = requireNotNull(invoiceDate),
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
