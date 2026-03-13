package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.SubscriptionChargeCreateRequest
import com.almonium.famsubbe.dto.SubscriptionChargeResponse
import com.almonium.famsubbe.dto.SubscriptionChargeUpdateRequest
import com.almonium.famsubbe.service.SubscriptionChargeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/admin/subscription-charges")
class AdminSubscriptionChargeController(
    private val chargeService: SubscriptionChargeService
) {

    @PostMapping
    fun createCharge(@Valid @RequestBody request: SubscriptionChargeCreateRequest): ResponseEntity<SubscriptionChargeResponse> {
        val charge = chargeService.createCharge(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(charge)
    }

    @PutMapping("/{id}")
    fun updateCharge(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SubscriptionChargeUpdateRequest
    ): ResponseEntity<SubscriptionChargeResponse> {
        val charge = chargeService.updateCharge(id, request)
        return ResponseEntity.ok(charge)
    }

    @GetMapping("/{id}")
    fun getCharge(@PathVariable id: UUID): ResponseEntity<SubscriptionChargeResponse> {
        val charge = chargeService.getCharge(id)
        return ResponseEntity.ok(charge)
    }

    @GetMapping("/service/{serviceId}")
    fun getChargesByService(@PathVariable serviceId: UUID): ResponseEntity<List<SubscriptionChargeResponse>> {
        val charges = chargeService.getChargesByService(serviceId)
        return ResponseEntity.ok(charges)
    }

    @DeleteMapping("/{id}")
    fun deleteCharge(@PathVariable id: UUID): ResponseEntity<Void> {
        chargeService.deleteCharge(id)
        return ResponseEntity.noContent().build()
    }
}
