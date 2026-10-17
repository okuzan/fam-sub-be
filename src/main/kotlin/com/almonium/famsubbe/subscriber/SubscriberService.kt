package com.almonium.famsubbe.subscriber

import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.invoice.InvoiceStatus
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.subscription.MembershipRepository
import com.almonium.famsubbe.util.BillingTime
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.*

@Service
@Transactional
class SubscriberService(
    private val subscriberRepository: SubscriberRepository,
    private val invoiceRepository: InvoiceRepository,
    private val membershipRepository: MembershipRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) {

    @Transactional(readOnly = true)
    fun getAllSubscribers(
        namePrefix: String? = null
    ): List<SubscriberResponse> {
        val subscribers = if (namePrefix != null) {
            subscriberRepository.findByNameIgnoreCaseStartingWith(namePrefix)
        } else {
            subscriberRepository.findAll()
        }
        return toResponses(subscribers)
    }

    @Transactional(readOnly = true)
    fun getSubscribersWithDebt(): List<SubscriberResponse> {
        val debtorIds = invoiceRepository.findSubscriberIdsWithUnpaidInvoices()
        return toResponses(subscriberRepository.findAllById(debtorIds))
    }

    @Transactional(readOnly = true)
    fun getSubscriberById(id: UUID): SubscriberResponse {
        val subscriber = subscriberRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Subscriber not found")
        return toResponses(listOf(subscriber)).single()
    }

    fun createSubscriber(request: SubscriberCreateRequest): SubscriberResponse {
        val subscriber = Subscriber().apply {
            name = request.name
            surname = request.surname.cleanSurname()
            email = request.email.lowercase().trim()
            balance = request.balance
            billingExempt = request.billingExempt
        }
        // Flushed so Hibernate fills createdAt/updatedAt: the bulk reads in
        // toResponses touch other tables and would not flush it on their own.
        val savedSubscriber = subscriberRepository.saveAndFlush(subscriber)
        return toResponses(listOf(savedSubscriber)).single()
    }

    fun updateSubscriber(id: UUID, request: SubscriberUpdateRequest): SubscriberResponse {
        val subscriber = subscriberRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Subscriber not found")
        val updatedBillingExempt = request.billingExempt ?: subscriber.billingExempt
        val billingExemptWasEnabled = !subscriber.billingExempt && updatedBillingExempt

        subscriber.apply {
            name = request.name
            surname = request.surname.cleanSurname()
            email = request.email.lowercase().trim()
            balance = request.balance
            billingExempt = updatedBillingExempt
        }

        val updatedSubscriber = subscriberRepository.saveAndFlush(subscriber)
        if (billingExemptWasEnabled) {
            invoiceRepository.findBySubscriberAndStatusIn(
                updatedSubscriber,
                listOf(InvoiceStatus.DRAFT, InvoiceStatus.SENT)
            ).forEach { invoice ->
                if (invoice.status != InvoiceStatus.PAID) {
                    invoice.status = InvoiceStatus.PAID
                    invoice.statusChangedAt = Instant.now()
                }
                invoiceRepository.save(invoice)
            }
        }
        return toResponses(listOf(updatedSubscriber)).single()
    }

    fun deleteSubscriber(id: UUID) {
        if (!subscriberRepository.existsById(id)) {
            throw EntityNotFoundException("Subscriber not found")
        }
        subscriberRepository.deleteById(id)
    }

    /**
     * Maps subscribers with a fixed handful of queries however many there are:
     * the archive fields need memberships, debts and payments, and fetching
     * them per subscriber made the list take seconds over a remote database.
     */
    private fun toResponses(subscribers: List<Subscriber>): List<SubscriberResponse> {
        if (subscribers.isEmpty()) return emptyList()
        val ids = subscribers.map { requireNotNull(it.id) }
        val membershipsBySubscriber = membershipRepository.findBySubscriberIdInWithService(ids)
            .groupBy { requireNotNull(it.subscriber?.id) }
        val owing = invoiceRepository.findSubscriberIdsWithUnpaidInvoices().toSet() +
            ledgerEntryRepository.findSubscriberIdsWithUninvoicedEntries()
        val lastPaidAt = invoiceRepository.findLastPaidAtBySubscriberIds(ids)
            .associate { row -> row[0] as UUID to row[1] as Instant? }
        val currentMonth = BillingTime.currentMonth()

        return subscribers.map { subscriber ->
            val subscriberId = requireNotNull(subscriber.id)
            val memberships = membershipsBySubscriber[subscriberId].orEmpty()
            val balance = subscriber.balance ?: BigDecimal.ZERO
            val endMonths = memberships.map { it.endMonth }
            SubscriberResponse(
                id = subscriberId,
                name = subscriber.name!!,
                surname = subscriber.surname,
                email = subscriber.email!!,
                balance = balance,
                billingExempt = subscriber.billingExempt,
                createdAt = subscriber.createdAt!!,
                updatedAt = subscriber.updatedAt!!,
                archived = isArchived(endMonths, subscriberId in owing, balance, currentMonth),
                formerServices = memberships.mapNotNull { it.subscriptionService?.name }.distinct().sorted(),
                lastMembershipEndMonth = endMonths.takeIf { months -> months.none { it == null } }
                    ?.filterNotNull()?.maxOrNull()?.toString(),
                lastInvoicePaidAt = lastPaidAt[subscriberId]
            )
        }
    }

    companion object {
        /**
         * Archived subscribers are former members with nothing left between
         * them and the group. Credit keeps someone active: it is still theirs.
         */
        fun isArchived(
            membershipEndMonths: List<YearMonth?>,
            owesSomething: Boolean,
            balance: BigDecimal,
            currentMonth: YearMonth
        ): Boolean =
            membershipEndMonths.all { it != null && it < currentMonth } &&
                !owesSomething &&
                balance.signum() == 0
    }

    private fun String?.cleanSurname(): String? = this?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
}
