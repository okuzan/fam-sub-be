package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.ChargeCreateRequest
import com.almonium.famsubbe.dto.ChargeResponse
import com.almonium.famsubbe.dto.ChargeUpdateRequest
import com.almonium.famsubbe.service.ChargeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.YearMonth
import java.util.*

@RestController
@RequestMapping("/admin/charges")
class AdminChargeController(
    private val chargeService: ChargeService
) {

    @PostMapping
    fun createCharge(@Valid @RequestBody request: ChargeCreateRequest): ResponseEntity<ChargeResponse> {
        val charge = chargeService.createCharge(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(charge)
    }

    @PutMapping("/{id}")
    fun updateCharge(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ChargeUpdateRequest
    ): ResponseEntity<ChargeResponse> {
        val charge = chargeService.updateCharge(id, request)
        return ResponseEntity.ok(charge)
    }

    @GetMapping("/{id}")
    fun getCharge(@PathVariable id: UUID): ResponseEntity<ChargeResponse> {
        val charge = chargeService.getCharge(id)
        return ResponseEntity.ok(charge)
    }

    @GetMapping("/service/{serviceId}")
    fun getChargesByService(@PathVariable serviceId: UUID): ResponseEntity<List<ChargeResponse>> {
        val charges = chargeService.getChargesByService(serviceId)
        return ResponseEntity.ok(charges)
    }

    @GetMapping("/month/{yearMonth}")
    fun getChargesByMonth(@PathVariable yearMonth: YearMonth): ResponseEntity<List<ChargeResponse>> {
        val charges = chargeService.getChargesByMonth(yearMonth)
        return ResponseEntity.ok(charges)
    }

    @DeleteMapping("/{id}")
    fun deleteCharge(@PathVariable id: UUID): ResponseEntity<Void> {
        chargeService.deleteCharge(id)
        return ResponseEntity.noContent().build()
    }
}
