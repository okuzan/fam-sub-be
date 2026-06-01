package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.AdminInviteAcceptRequest
import com.almonium.famsubbe.dto.AdminInviteAcceptResponse
import com.almonium.famsubbe.dto.AdminInviteCreateRequest
import com.almonium.famsubbe.dto.AdminInviteResponse
import com.almonium.famsubbe.entity.AdminActionTargetType
import com.almonium.famsubbe.entity.AdminActionType
import com.almonium.famsubbe.service.AccountService
import com.almonium.famsubbe.service.AdminAuditLogService
import com.almonium.famsubbe.service.AdminInviteService
import com.almonium.famsubbe.util.AuthenticationUtil
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/admin/invites")
class AdminInviteController(
    private val adminInviteService: AdminInviteService,
    private val accountService: AccountService,
    private val adminAuditLogService: AdminAuditLogService
) {

    @GetMapping
    fun getInvites(): ResponseEntity<List<AdminInviteResponse>> =
        ResponseEntity.ok(adminInviteService.getInvites())

    @PostMapping
    fun createInvite(
        @Valid @RequestBody request: AdminInviteCreateRequest,
        authentication: Authentication
    ): ResponseEntity<AdminInviteResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invite = adminInviteService.createInvite(request, performedByAccountId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.ADMIN_INVITE_CREATED,
            targetType = AdminActionTargetType.ADMIN_INVITE,
            targetId = invite.id,
            summary = "Created admin invite for ${invite.email}",
            metadata = mapOf("email" to invite.email, "expiresAt" to invite.expiresAt)
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(invite)
    }

    @PostMapping("/{inviteId}/revoke")
    fun revokeInvite(
        @PathVariable inviteId: UUID,
        authentication: Authentication
    ): ResponseEntity<AdminInviteResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val invite = adminInviteService.revokeInvite(inviteId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.ADMIN_INVITE_REVOKED,
            targetType = AdminActionTargetType.ADMIN_INVITE,
            targetId = invite.id,
            summary = "Revoked admin invite for ${invite.email}",
            metadata = mapOf("email" to invite.email)
        )
        return ResponseEntity.ok(invite)
    }
}

@RestController
@RequestMapping("/admin-invites")
class AdminInviteAcceptanceController(
    private val adminInviteService: AdminInviteService,
    private val accountService: AccountService,
    private val adminAuditLogService: AdminAuditLogService
) {

    @PostMapping("/accept")
    fun acceptInvite(
        @Valid @RequestBody request: AdminInviteAcceptRequest,
        authentication: Authentication
    ): ResponseEntity<AdminInviteAcceptResponse> {
        val account = AuthenticationUtil.resolveAccount(authentication, accountService)
            ?: throw IllegalStateException("Authenticated account not found")
        val accountId = requireNotNull(account.id)
        val result = adminInviteService.acceptInvite(request.token, account)
        adminAuditLogService.log(
            createdByAccountId = accountId,
            actionType = AdminActionType.ADMIN_INVITE_ACCEPTED,
            targetType = AdminActionTargetType.ADMIN_INVITE,
            summary = "Accepted admin invite for ${result.email}",
            metadata = mapOf("email" to result.email)
        )
        return ResponseEntity.ok(result)
    }
}
