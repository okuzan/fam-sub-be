package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.SubscriberCreateRequest
import com.almonium.famsubbe.dto.SubscriberResponse
import com.almonium.famsubbe.dto.SubscriberUpdateRequest
import com.almonium.famsubbe.entity.Subscriber
import com.almonium.famsubbe.repository.SubscriberRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*

@Service
class SubscriberService(
    private val subscriberRepository: SubscriberRepository
) {

    fun getAllSubscribers(): List<SubscriberResponse> {
        return subscriberRepository.findAll().map { it.toResponse() }
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
        }
        val savedSubscriber = subscriberRepository.save(subscriber)
        return savedSubscriber.toResponse()
    }

    fun updateSubscriber(id: UUID, request: SubscriberUpdateRequest): SubscriberResponse {
        val subscriber = subscriberRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Subscriber not found")
        
        subscriber.apply {
            name = request.name
            email = request.email.lowercase().trim()
            balance = request.balance
        }
        
        val updatedSubscriber = subscriberRepository.save(subscriber)
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
            createdAt = this.createdAt!!,
            updatedAt = this.updatedAt!!
        )
    }
}
