package com.almonium.famsubbe.account

import com.almonium.famsubbe.admin.AdminActionTargetType
import com.almonium.famsubbe.admin.AdminActionType
import com.almonium.famsubbe.admin.AdminAuditLogService
import com.almonium.famsubbe.util.AuthenticationUtil
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/admin/accounts")
class AdminAccountController(
    private val accountService: AccountService,
    private val adminAuditLogService: AdminAuditLogService
) {

    @GetMapping
    fun getAccounts(): ResponseEntity<List<AccountResponse>> =
        ResponseEntity.ok(accountService.getAccounts())

    @DeleteMapping("/{accountId}")
    fun deleteAccount(
        @PathVariable accountId: UUID,
        authentication: Authentication
    ): ResponseEntity<AccountResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val deletedAccount = accountService.deleteAccount(accountId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.ACCOUNT_DELETED,
            targetType = AdminActionTargetType.ACCOUNT,
            targetId = accountId,
            summary = "Deleted account ${deletedAccount.email}",
            metadata = mapOf(
                "email" to deletedAccount.email,
                "roles" to deletedAccount.roles
            )
        )
        return ResponseEntity.ok(deletedAccount)
    }
}
