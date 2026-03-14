package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.SubscriberCreateRequest
import com.almonium.famsubbe.dto.SubscriberResponse
import com.almonium.famsubbe.dto.SubscriberUpdateRequest
import com.almonium.famsubbe.service.SubscriberService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/admin/subscribers")
class AdminSubscriberController(
    private val subscriberService: SubscriberService
) {

    @GetMapping
    fun getAllSubscribers(): ResponseEntity<List<SubscriberResponse>> {
        val subscribers = subscriberService.getAllSubscribers()
        return ResponseEntity.ok(subscribers)
    }

    @GetMapping("/{id}")
    fun getSubscriberById(@PathVariable id: UUID): ResponseEntity<SubscriberResponse> {
        val subscriber = subscriberService.getSubscriberById(id)
        return ResponseEntity.ok(subscriber)
    }

    @PostMapping
    fun createSubscriber(@Valid @RequestBody request: SubscriberCreateRequest): ResponseEntity<SubscriberResponse> {
        val subscriber = subscriberService.createSubscriber(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriber)
    }

    @PutMapping("/{id}")
    fun updateSubscriber(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SubscriberUpdateRequest
    ): ResponseEntity<SubscriberResponse> {
        val subscriber = subscriberService.updateSubscriber(id, request)
        return ResponseEntity.ok(subscriber)
    }

    @DeleteMapping("/{id}")
    fun deleteSubscriber(@PathVariable id: UUID): ResponseEntity<Void> {
        subscriberService.deleteSubscriber(id)
        return ResponseEntity.noContent().build()
    }
}
