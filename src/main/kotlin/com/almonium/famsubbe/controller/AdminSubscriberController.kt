package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.SubscriberCreateRequest
import com.almonium.famsubbe.dto.SubscriberDetailResponse
import com.almonium.famsubbe.dto.SubscriberResponse
import com.almonium.famsubbe.dto.SubscriberUpdateRequest
import com.almonium.famsubbe.service.InvoiceEmailService
import com.almonium.famsubbe.service.InvoiceService
import com.almonium.famsubbe.service.SubscriberService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/admin/subscribers")
class AdminSubscriberController(
    private val subscriberService: SubscriberService,
    private val invoiceService: InvoiceService,
    private val invoiceEmailService: InvoiceEmailService
) {

    @GetMapping
    fun getAllSubscribers(
        @RequestParam(required = false) namePrefix: String?
    ): ResponseEntity<List<SubscriberResponse>> {
        val subscribers = subscriberService.getAllSubscribers(namePrefix)
        return ResponseEntity.ok(subscribers)
    }

    @GetMapping("/debtors")
    fun getSubscribersWithDebt(): ResponseEntity<List<SubscriberResponse>> {
        val subscribers = subscriberService.getSubscribersWithDebt()
        return ResponseEntity.ok(subscribers)
    }

    @GetMapping("/{id}")
    fun getSubscriberById(@PathVariable id: UUID): ResponseEntity<SubscriberResponse> {
        val subscriber = subscriberService.getSubscriberById(id)
        return ResponseEntity.ok(subscriber)
    }

    @GetMapping("/{id}/details")
    fun getSubscriberDetails(@PathVariable id: UUID): ResponseEntity<SubscriberDetailResponse> {
        val subscriberDetails = invoiceService.getSubscriberDetails(id)
        return ResponseEntity.ok(subscriberDetails)
    }

    @PostMapping("/{id}/email-situation")
    fun emailSubscriberSituation(@PathVariable id: UUID): ResponseEntity<Map<String, String>> {
        val details = invoiceService.getSubscriberDetails(id)
        
        if (details.email.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Subscriber has no email address"))
        }

        val success = invoiceEmailService.sendSituationEmail(
            toEmail = details.email,
            subscriberName = details.name,
            totalOwed = details.totalAmountOwed,
            unpaidInvoicesCount = details.unpaidInvoices.size,
            activeSubscriptionsCount = details.activeSubscriptions.size,
            activeSubscriptionNames = details.activeSubscriptions.map { it.serviceName }
        )

        return if (success) {
            ResponseEntity.ok(mapOf("message" to "Situation email sent successfully to ${details.email}"))
        } else {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to send email"))
        }
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
