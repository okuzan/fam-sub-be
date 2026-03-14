package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.SubscriptionServiceCreateRequest
import com.almonium.famsubbe.dto.SubscriptionServiceResponse
import com.almonium.famsubbe.dto.SubscriptionServiceUpdateRequest
import com.almonium.famsubbe.entity.SubscriptionService
import com.almonium.famsubbe.repository.SubscriptionServiceRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.*

@Service
class SubscriptionServiceService(
    private val subscriptionServiceRepository: SubscriptionServiceRepository
) {

    fun getAllServices(): List<SubscriptionServiceResponse> {
        return subscriptionServiceRepository.findAll().map { it.toResponse() }
    }

    fun getServiceById(id: UUID): SubscriptionServiceResponse {
        val service = subscriptionServiceRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Subscription service not found")
        return service.toResponse()
    }

    fun createService(request: SubscriptionServiceCreateRequest): SubscriptionServiceResponse {
        val service = SubscriptionService().apply {
            name = request.name
            price = request.price
        }
        val savedService = subscriptionServiceRepository.save(service)
        return savedService.toResponse()
    }

    fun updateService(id: UUID, request: SubscriptionServiceUpdateRequest): SubscriptionServiceResponse {
        val service = subscriptionServiceRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Subscription service not found")
        
        service.apply {
            name = request.name
            price = request.price
        }
        
        val updatedService = subscriptionServiceRepository.save(service)
        return updatedService.toResponse()
    }

    fun deleteService(id: UUID) {
        if (!subscriptionServiceRepository.existsById(id)) {
            throw EntityNotFoundException("Subscription service not found")
        }
        subscriptionServiceRepository.deleteById(id)
    }

    private fun SubscriptionService.toResponse(): SubscriptionServiceResponse {
        return SubscriptionServiceResponse(
            id = this.id!!,
            name = this.name!!,
            price = this.price!!,
            createdAt = this.createdAt!!,
            updatedAt = this.updatedAt!!
        )
    }
}
