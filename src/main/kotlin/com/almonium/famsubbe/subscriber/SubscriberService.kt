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
        return subscribers.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getSubscribersWithDebt(): List<SubscriberResponse> {
        val debtorIds = invoiceRepository.findSubscriberIdsWithUnpaidInvoices()
        return subscriberRepository.findAllById(debtorIds).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getSubscriberById(id: UUID): SubscriberResponse {
        val subscriber = subscriberRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Subscriber not found")
        return subscriber.toResponse()
    }

    fun createSubscriber(request: SubscriberCreateRequest): SubscriberResponse {
        val subscriber = Subscriber().apply {
            name = request.name
            surname = request.surname.cleanSurname()
            email = request.email.lowercase().trim()
            balance = request.balance
            billingExempt = request.billingExempt
        }
        val savedSubscriber = subscriberRepository.save(subscriber)
        return savedSubscriber.toResponse()
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

        val updatedSubscriber = subscriberRepository.save(subscriber)
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
        return updatedSubscriber.toResponse()
    }

    fun deleteSubscriber(id: UUID) {
        if (!subscriberRepository.existsById(id)) {
            throw EntityNotFoundException("Subscriber not found")
        }
        subscriberRepository.deleteById(id)
    }

    private fun Subscriber.toResponse(): SubscriberResponse {
        val subscriberId = requireNotNull(id)
        val memberships = membershipRepository.findBySubscriber(this)
        val balance = balance ?: BigDecimal.ZERO
        val owesSomething = invoiceRepository.findBySubscriberAndStatusIn(this, SubscriberStandingService.UNPAID_STATUSES).isNotEmpty() ||
            ledgerEntryRepository.existsBySubscriberIdAndInvoiceIsNull(subscriberId)
        val endMonths = memberships.map { it.endMonth }

        return SubscriberResponse(
            id = subscriberId,
            name = this.name!!,
            surname = this.surname,
            email = this.email!!,
            balance = balance,
            billingExempt = this.billingExempt,
            createdAt = this.createdAt!!,
            updatedAt = this.updatedAt!!,
            archived = isArchived(endMonths, owesSomething, balance, BillingTime.currentMonth()),
            formerServices = memberships.mapNotNull { it.subscriptionService?.name }.distinct().sorted(),
            lastMembershipEndMonth = endMonths.takeIf { months -> months.none { it == null } }
                ?.filterNotNull()?.maxOrNull()?.toString(),
            lastInvoicePaidAt = invoiceRepository.findBySubscriberAndStatusIn(this, listOf(InvoiceStatus.PAID))
                .mapNotNull { it.statusChangedAt }.maxOrNull()
        )
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
