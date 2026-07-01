package com.almonium.famsubbe.subscription

import com.almonium.famsubbe.entity.AdminActionTargetType
import com.almonium.famsubbe.entity.AdminActionType
import com.almonium.famsubbe.account.AccountService
import com.almonium.famsubbe.service.AdminAuditLogService
import com.almonium.famsubbe.util.AuthenticationUtil
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth
import java.util.*

@RestController
@RequestMapping("/admin/memberships")
class AdminMembershipController(
    private val membershipService: MembershipService,
    private val accountService: AccountService,
    private val adminAuditLogService: AdminAuditLogService
) {

    @PostMapping
    fun createMembership(
        @Valid @RequestBody request: MembershipCreateRequest,
        authentication: Authentication
    ): ResponseEntity<MembershipResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val membership = membershipService.createMembership(request)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.MEMBERSHIP_CREATED,
            targetType = AdminActionTargetType.MEMBERSHIP,
            targetId = membership.id,
            subscriberId = membership.subscriberId,
            fromMonth = membership.startMonth,
            toMonth = membership.endMonth,
            summary = "Created ${membership.subscriptionServiceName} membership for ${membership.subscriberName}",
            metadata = mapOf(
                "subscriptionServiceId" to membership.subscriptionServiceId,
                "subscriptionServiceName" to membership.subscriptionServiceName
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(membership)
    }

    @PutMapping("/{id}")
    fun updateMembership(
        @PathVariable id: UUID,
        @Valid @RequestBody request: MembershipUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<MembershipResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val membership = membershipService.updateMembership(id, request)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.MEMBERSHIP_UPDATED,
            targetType = AdminActionTargetType.MEMBERSHIP,
            targetId = id,
            subscriberId = membership.subscriberId,
            fromMonth = membership.startMonth,
            toMonth = membership.endMonth,
            summary = "Updated ${membership.subscriptionServiceName} membership for ${membership.subscriberName}",
            metadata = mapOf("subscriptionServiceId" to membership.subscriptionServiceId)
        )
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
        @Valid @RequestBody request: MembershipEndRequest,
        authentication: Authentication
    ): ResponseEntity<MembershipResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val membership = membershipService.endMembership(id, request)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.MEMBERSHIP_ENDED,
            targetType = AdminActionTargetType.MEMBERSHIP,
            targetId = id,
            subscriberId = membership.subscriberId,
            fromMonth = membership.startMonth,
            toMonth = membership.endMonth,
            summary = "Ended ${membership.subscriptionServiceName} membership for ${membership.subscriberName}",
            metadata = mapOf("subscriptionServiceId" to membership.subscriptionServiceId)
        )
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
    fun deleteMembership(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val membership = membershipService.getMembership(id)
        membershipService.deleteMembership(id)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.MEMBERSHIP_DELETED,
            targetType = AdminActionTargetType.MEMBERSHIP,
            targetId = id,
            subscriberId = membership.subscriberId,
            fromMonth = membership.startMonth,
            toMonth = membership.endMonth,
            summary = "Deleted ${membership.subscriptionServiceName} membership for ${membership.subscriberName}",
            metadata = mapOf("subscriptionServiceId" to membership.subscriptionServiceId)
        )
        return ResponseEntity.noContent().build()
    }
}
