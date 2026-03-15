package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.MembershipCreateRequest
import com.almonium.famsubbe.dto.MembershipEndRequest
import com.almonium.famsubbe.dto.MembershipResponse
import com.almonium.famsubbe.dto.MembershipUpdateRequest
import com.almonium.famsubbe.service.MembershipService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.YearMonth
import java.util.*

@RestController
@RequestMapping("/admin/memberships")
class AdminMembershipController(
    private val membershipService: MembershipService
) {

    @PostMapping
    fun createMembership(@Valid @RequestBody request: MembershipCreateRequest): ResponseEntity<MembershipResponse> {
        val membership = membershipService.createMembership(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(membership)
    }

    @PutMapping("/{id}")
    fun updateMembership(
        @PathVariable id: UUID,
        @Valid @RequestBody request: MembershipUpdateRequest
    ): ResponseEntity<MembershipResponse> {
        val membership = membershipService.updateMembership(id, request)
        return ResponseEntity.ok(membership)
    }

    @GetMapping("/{id}")
    fun getMembership(@PathVariable id: UUID): ResponseEntity<MembershipResponse> {
        val membership = membershipService.getMembership(id)
        return ResponseEntity.ok(membership)
    }

    @PostMapping("/{id}/end")
    fun endMembership(
        @PathVariable id: UUID,
        @Valid @RequestBody request: MembershipEndRequest
    ): ResponseEntity<MembershipResponse> {
        val membership = membershipService.endMembership(id, request)
        return ResponseEntity.ok(membership)
    }

    @GetMapping("/service/{serviceId}/active/{yearMonth}")
    fun getActiveMembershipsByServiceAndMonth(
        @PathVariable serviceId: UUID,
        @PathVariable yearMonth: YearMonth
    ): ResponseEntity<List<MembershipResponse>> {
        val memberships = membershipService.getMembershipsByServiceAndMonth(serviceId, yearMonth)
        return ResponseEntity.ok(memberships)
    }

    @GetMapping("/subscriber/{subscriberId}/active/{yearMonth}")
    fun getActiveMembershipsBySubscriberAndMonth(
        @PathVariable subscriberId: UUID,
        @PathVariable yearMonth: YearMonth
    ): ResponseEntity<List<MembershipResponse>> {
        val memberships = membershipService.getMembershipsBySubscriberAndMonth(subscriberId, yearMonth)
        return ResponseEntity.ok(memberships)
    }

    @GetMapping("/service/{serviceId}")
    fun getMembershipsByService(@PathVariable serviceId: UUID): ResponseEntity<List<MembershipResponse>> {
        val memberships = membershipService.getMembershipsByService(serviceId)
        return ResponseEntity.ok(memberships)
    }

    @DeleteMapping("/{id}")
    fun deleteMembership(@PathVariable id: UUID): ResponseEntity<Void> {
        membershipService.deleteMembership(id)
        return ResponseEntity.noContent().build()
    }
}
