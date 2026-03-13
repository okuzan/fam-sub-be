package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.SubscriptionMembershipCreateRequest
import com.almonium.famsubbe.dto.SubscriptionMembershipResponse
import com.almonium.famsubbe.dto.SubscriptionMembershipUpdateRequest
import com.almonium.famsubbe.service.SubscriptionMembershipService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.YearMonth
import java.util.*

@RestController
@RequestMapping("/admin/subscription-memberships")
class AdminSubscriptionMembershipController(
    private val membershipService: SubscriptionMembershipService
) {

    @PostMapping
    fun createMembership(@Valid @RequestBody request: SubscriptionMembershipCreateRequest): ResponseEntity<SubscriptionMembershipResponse> {
        val membership = membershipService.createMembership(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(membership)
    }

    @PutMapping("/{id}")
    fun updateMembership(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SubscriptionMembershipUpdateRequest
    ): ResponseEntity<SubscriptionMembershipResponse> {
        val membership = membershipService.updateMembership(id, request)
        return ResponseEntity.ok(membership)
    }

    @GetMapping("/{id}")
    fun getMembership(@PathVariable id: UUID): ResponseEntity<SubscriptionMembershipResponse> {
        val membership = membershipService.getMembership(id)
        return ResponseEntity.ok(membership)
    }

    @GetMapping("/service/{serviceId}/month/{yearMonth}")
    fun getMembershipsByServiceAndMonth(
        @PathVariable serviceId: UUID,
        @PathVariable yearMonth: YearMonth
    ): ResponseEntity<List<SubscriptionMembershipResponse>> {
        val memberships = membershipService.getMembershipsByServiceAndMonth(serviceId, yearMonth)
        return ResponseEntity.ok(memberships)
    }

    @GetMapping("/account/{accountId}")
    fun getMembershipsByAccount(@PathVariable accountId: UUID): ResponseEntity<List<SubscriptionMembershipResponse>> {
        val memberships = membershipService.getMembershipsByAccount(accountId)
        return ResponseEntity.ok(memberships)
    }

    @GetMapping("/service/{serviceId}")
    fun getMembershipsByService(@PathVariable serviceId: UUID): ResponseEntity<List<SubscriptionMembershipResponse>> {
        val memberships = membershipService.getMembershipsByService(serviceId)
        return ResponseEntity.ok(memberships)
    }

    @DeleteMapping("/{id}")
    fun deleteMembership(@PathVariable id: UUID): ResponseEntity<Void> {
        membershipService.deleteMembership(id)
        return ResponseEntity.noContent().build()
    }
}
