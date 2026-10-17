package com.almonium.famsubbe.subscription

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class SubscriptionServiceService(
    private val subscriptionServiceRepository: SubscriptionServiceRepository
) {

    @Transactional(readOnly = true)
    fun getAllServices(): List<SubscriptionServiceResponse> {
        return subscriptionServiceRepository.findAll().map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getServiceById(id: UUID): SubscriptionServiceResponse {
        val service = subscriptionServiceRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Subscription service not found")
        return service.toResponse()
    }

    fun createService(request: SubscriptionServiceCreateRequest): SubscriptionServiceResponse {
        val service = SubscriptionService().apply {
            name = request.name
            price = request.price
            currency = request.currency
            fixedMonthlyCharge = request.fixedMonthlyCharge
        }
        service.requireFixedInUah()
        // Flushed so Hibernate fills createdAt/updatedAt before the response reads them.
        val savedService = subscriptionServiceRepository.saveAndFlush(service)
        return savedService.toResponse()
    }

    fun updateService(id: UUID, request: SubscriptionServiceUpdateRequest): SubscriptionServiceResponse {
        val service = subscriptionServiceRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Subscription service not found")
        
        service.apply {
            name = request.name
            price = request.price
            request.currency?.let { currency = it }
            request.fixedMonthlyCharge?.let { fixedMonthlyCharge = it }
        }
        service.requireFixedInUah()
        
        val updatedService = subscriptionServiceRepository.saveAndFlush(service)
        return updatedService.toResponse()
    }

    fun deleteService(id: UUID) {
        if (!subscriptionServiceRepository.existsById(id)) {
            throw EntityNotFoundException("Subscription service not found")
        }
        subscriptionServiceRepository.deleteById(id)
    }

    /** A fixed monthly charge is the price itself, so it has to be in hryvnias already. */
    private fun SubscriptionService.requireFixedInUah() {
        require(!fixedMonthlyCharge || currency == SubscriptionService.UAH) {
            "Only a price in UAH can be charged automatically every month"
        }
    }

    private fun SubscriptionService.toResponse(): SubscriptionServiceResponse {
        return SubscriptionServiceResponse(
            id = this.id!!,
            name = this.name!!,
            price = this.price!!,
            currency = this.currency,
            fixedMonthlyCharge = this.fixedMonthlyCharge,
            createdAt = this.createdAt!!,
            updatedAt = this.updatedAt!!
        )
    }
}
