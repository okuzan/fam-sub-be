package com.almonium.famsubbe.subscriber

import com.almonium.famsubbe.invoice.InvoiceStatus
import com.almonium.famsubbe.invoice.InvoiceRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@Service
class SubscriberService(
    private val subscriberRepository: SubscriberRepository,
    private val invoiceRepository: InvoiceRepository
) {

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

    fun getSubscribersWithDebt(): List<SubscriberResponse> {
        val debtorIds = invoiceRepository.findSubscriberIdsWithUnpaidInvoices()
        return subscriberRepository.findAllById(debtorIds).map { it.toResponse() }
    }

    fun getSubscriberById(id: UUID): SubscriberResponse {
        val subscriber = subscriberRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Subscriber not found")
        return subscriber.toResponse()
    }

    fun createSubscriber(request: SubscriberCreateRequest): SubscriberResponse {
        val subscriber = Subscriber().apply {
            name = request.name
            email = request.email.lowercase().trim()
            balance = request.balance
            autoPayInvoices = request.autoPayInvoices
        }
        val savedSubscriber = subscriberRepository.save(subscriber)
        return savedSubscriber.toResponse()
    }

    fun updateSubscriber(id: UUID, request: SubscriberUpdateRequest): SubscriberResponse {
        val subscriber = subscriberRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Subscriber not found")
        val updatedAutoPayInvoices = request.autoPayInvoices ?: subscriber.autoPayInvoices
        val autoPayInvoicesWasEnabled = !subscriber.autoPayInvoices && updatedAutoPayInvoices

        subscriber.apply {
            name = request.name
            email = request.email.lowercase().trim()
            balance = request.balance
            autoPayInvoices = updatedAutoPayInvoices
        }

        val updatedSubscriber = subscriberRepository.save(subscriber)
        if (autoPayInvoicesWasEnabled) {
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
        return SubscriberResponse(
            id = this.id!!,
            name = this.name!!,
            email = this.email!!,
            balance = this.balance ?: BigDecimal.ZERO,
            autoPayInvoices = this.autoPayInvoices,
            createdAt = this.createdAt!!,
            updatedAt = this.updatedAt!!
        )
    }
}
