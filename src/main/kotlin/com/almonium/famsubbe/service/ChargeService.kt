package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.ChargeCreateRequest
import com.almonium.famsubbe.dto.ChargeResponse
import com.almonium.famsubbe.dto.ChargeUpdateRequest
import com.almonium.famsubbe.entity.Charge
import com.almonium.famsubbe.repository.ChargeRepository
import com.almonium.famsubbe.repository.SubscriptionServiceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class ChargeService(
    private val chargeRepository: ChargeRepository,
    private val subscriptionServiceRepository: SubscriptionServiceRepository
) {

    fun createCharge(request: ChargeCreateRequest): ChargeResponse {
        val subscriptionService = subscriptionServiceRepository.findById(request.subscriptionServiceId)
            .orElseThrow { IllegalArgumentException("Subscription service not found: ${request.subscriptionServiceId}") }

        // Check if charge already exists for this service and month
        val existingCharge = chargeRepository.findBySubscriptionServiceAndChargeMonth(
            subscriptionService, request.chargeMonth
        )
        if (existingCharge != null) {
            throw IllegalArgumentException("Charge already exists for ${subscriptionService.name} in ${request.chargeMonth}")
        }

        val charge = Charge().apply {
            this.subscriptionService = subscriptionService
            this.amount = request.amount
            this.chargeMonth = request.chargeMonth
        }

        val savedCharge = chargeRepository.save(charge)
        return mapToResponse(savedCharge)
    }

    fun updateCharge(chargeId: UUID, request: ChargeUpdateRequest): ChargeResponse {
        val charge = chargeRepository.findById(chargeId)
            .orElseThrow { IllegalArgumentException("Charge not found: $chargeId") }

        charge.amount = request.amount

        val updatedCharge = chargeRepository.save(charge)
        return mapToResponse(updatedCharge)
    }

    fun getCharge(chargeId: UUID): ChargeResponse {
        val charge = chargeRepository.findById(chargeId)
            .orElseThrow { IllegalArgumentException("Charge not found: $chargeId") }
        return mapToResponse(charge)
    }

    fun getChargesByService(subscriptionServiceId: UUID): List<ChargeResponse> {
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

    private fun mapToResponse(charge: Charge): ChargeResponse {
        return ChargeResponse(
            id = charge.id!!,
            subscriptionServiceId = charge.subscriptionService!!.id!!,
            subscriptionServiceName = charge.subscriptionService!!.name ?: "",
            amount = charge.amount!!,
            chargeMonth = charge.chargeMonth!!,
            createdAt = charge.createdAt ?: Date(), // Fallback to current time if null
            updatedAt = charge.updatedAt ?: Date()  // Fallback to current time if null
        )
    }
}
