package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.SubscriptionChargeCreateRequest
import com.almonium.famsubbe.dto.SubscriptionChargeResponse
import com.almonium.famsubbe.dto.SubscriptionChargeUpdateRequest
import com.almonium.famsubbe.entity.SubscriptionCharge
import com.almonium.famsubbe.repository.SubscriptionChargeRepository
import com.almonium.famsubbe.repository.SubscriptionServiceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class SubscriptionChargeService(
    private val chargeRepository: SubscriptionChargeRepository,
    private val subscriptionServiceRepository: SubscriptionServiceRepository
) {

    fun createCharge(request: SubscriptionChargeCreateRequest): SubscriptionChargeResponse {
        val subscriptionService = subscriptionServiceRepository.findById(request.subscriptionServiceId)
            .orElseThrow { IllegalArgumentException("Subscription service not found: ${request.subscriptionServiceId}") }

        // Check if charge already exists for this service and month
        val existingCharge = chargeRepository.findBySubscriptionServiceAndChargeDate(
            subscriptionService, request.chargeDate
        )
        if (existingCharge != null) {
            throw IllegalArgumentException("Charge already exists for ${subscriptionService.name} in ${request.chargeDate}")
        }

        val charge = SubscriptionCharge().apply {
            this.subscriptionService = subscriptionService
            this.amount = request.amount
            this.chargeDate = request.chargeDate
        }

        val savedCharge = chargeRepository.save(charge)
        return mapToResponse(savedCharge)
    }

    fun updateCharge(chargeId: UUID, request: SubscriptionChargeUpdateRequest): SubscriptionChargeResponse {
        val charge = chargeRepository.findById(chargeId)
            .orElseThrow { IllegalArgumentException("Charge not found: $chargeId") }

        charge.amount = request.amount

        val updatedCharge = chargeRepository.save(charge)
        return mapToResponse(updatedCharge)
    }

    fun getCharge(chargeId: UUID): SubscriptionChargeResponse {
        val charge = chargeRepository.findById(chargeId)
            .orElseThrow { IllegalArgumentException("Charge not found: $chargeId") }
        return mapToResponse(charge)
    }

    fun getChargesByService(subscriptionServiceId: UUID): List<SubscriptionChargeResponse> {
        val subscriptionService = subscriptionServiceRepository.findById(subscriptionServiceId)
            .orElseThrow { IllegalArgumentException("Subscription service not found: $subscriptionServiceId") }
        
        return chargeRepository.findBySubscriptionService(subscriptionService).map { mapToResponse(it) }
    }

    fun deleteCharge(chargeId: UUID) {
        if (!chargeRepository.existsById(chargeId)) {
            throw IllegalArgumentException("Charge not found: $chargeId")
        }
        chargeRepository.deleteById(chargeId)
    }

    private fun mapToResponse(charge: SubscriptionCharge): SubscriptionChargeResponse {
        return SubscriptionChargeResponse(
            id = charge.id!!,
            subscriptionServiceId = charge.subscriptionService!!.id!!,
            subscriptionServiceName = charge.subscriptionService!!.name ?: "",
            amount = charge.amount!!,
            chargeDate = charge.chargeDate!!,
            createdAt = charge.createdAt!!,
            updatedAt = charge.updatedAt!!
        )
    }
}
