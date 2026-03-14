package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.SubscriptionServiceCreateRequest
import com.almonium.famsubbe.dto.SubscriptionServiceResponse
import com.almonium.famsubbe.dto.SubscriptionServiceUpdateRequest
import com.almonium.famsubbe.service.SubscriptionServiceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/admin/subscription-services")
class AdminSubscriptionServiceController(
    private val subscriptionServiceService: SubscriptionServiceService
) {

    @GetMapping
    fun getAllServices(): ResponseEntity<List<SubscriptionServiceResponse>> {
        val services = subscriptionServiceService.getAllServices()
        return ResponseEntity.ok(services)
    }

    @GetMapping("/{id}")
    fun getServiceById(@PathVariable id: UUID): ResponseEntity<SubscriptionServiceResponse> {
        val service = subscriptionServiceService.getServiceById(id)
        return ResponseEntity.ok(service)
    }

    @PostMapping
    fun createService(@Valid @RequestBody request: SubscriptionServiceCreateRequest): ResponseEntity<SubscriptionServiceResponse> {
        val service = subscriptionServiceService.createService(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(service)
    }

    @PutMapping("/{id}")
    fun updateService(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SubscriptionServiceUpdateRequest
    ): ResponseEntity<SubscriptionServiceResponse> {
        val service = subscriptionServiceService.updateService(id, request)
        return ResponseEntity.ok(service)
    }

    @DeleteMapping("/{id}")
    fun deleteService(@PathVariable id: UUID): ResponseEntity<Void> {
        subscriptionServiceService.deleteService(id)
        return ResponseEntity.noContent().build()
    }
}
