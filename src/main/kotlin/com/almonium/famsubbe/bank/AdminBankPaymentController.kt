package com.almonium.famsubbe.bank

import com.almonium.famsubbe.account.AccountService
import com.almonium.famsubbe.admin.AdminActionTargetType
import com.almonium.famsubbe.admin.AdminActionType
import com.almonium.famsubbe.admin.AdminAuditLogService
import com.almonium.famsubbe.util.AuthenticationUtil
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.*

@RestController
@RequestMapping("/admin/bank-payments")
class AdminBankPaymentController(
    private val bankPaymentService: BankPaymentService,
    private val bankSyncService: BankSyncService,
    private val accountService: AccountService,
    private val adminAuditLogService: AdminAuditLogService
) {
    @GetMapping
    fun getBankPayments(
        @RequestParam(required = false) status: BankTransactionStatus?,
        @RequestParam(defaultValue = "200") limit: Int
    ): ResponseEntity<BankPaymentsOverview> =
        ResponseEntity.ok(
            BankPaymentsOverview(
                sync = bankSyncService.status(),
                transactions = bankPaymentService.getTransactions(status, limit)
            )
        )

    @PostMapping("/sync")
    fun sync(authentication: Authentication): ResponseEntity<BankSyncResult> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val result = try {
            bankSyncService.sync()
        } catch (e: MonoRateLimitedException) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message)
        }
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.BANK_PAYMENTS_SYNCED,
            targetType = AdminActionTargetType.BANK_TRANSACTION,
            summary = "Synced bank payments: ${result.importedCount} new, ${result.appliedCount} applied, " +
                "${result.needsReviewCount} to review",
            metadata = mapOf(
                "fetchedCount" to result.fetchedCount,
                "importedCount" to result.importedCount,
                "appliedCount" to result.appliedCount,
                "needsReviewCount" to result.needsReviewCount,
                "ignoredCount" to result.ignoredCount,
                "syncedTo" to result.syncedTo.toString()
            )
        )
        return ResponseEntity.ok(result)
    }

    @PostMapping("/{id}/assign")
    fun assign(
        @PathVariable id: UUID,
        @RequestBody request: BankPaymentAssignRequest,
        authentication: Authentication
    ): ResponseEntity<BankPaymentResolution> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val resolution = bankPaymentService.assign(id, request, performedByAccountId)
        val transaction = resolution.transaction
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.BANK_PAYMENT_ASSIGNED,
            targetType = AdminActionTargetType.BANK_TRANSACTION,
            targetId = id,
            subscriberId = transaction.subscriberId,
            summary = "Applied bank payment of ${transaction.amount} from ${transaction.senderName} " +
                "to ${transaction.subscriberName}, settling ${resolution.settledInvoiceIds.size} invoices",
            metadata = mapOf(
                "amount" to transaction.amount,
                "senderName" to transaction.senderName,
                "occurredAt" to transaction.occurredAt.toString(),
                "settledInvoiceIds" to resolution.settledInvoiceIds,
                "settledAmount" to transaction.settledAmount,
                "balanceAfter" to resolution.balanceAfter,
                "senderRemembered" to resolution.senderRemembered
            )
        )
        return ResponseEntity.ok(resolution)
    }

    @PostMapping("/{id}/ignore")
    fun ignore(
        @PathVariable id: UUID,
        @RequestBody request: BankPaymentIgnoreRequest,
        authentication: Authentication
    ): ResponseEntity<BankPaymentResolution> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val resolution = bankPaymentService.ignore(id, request, performedByAccountId)
        val transaction = resolution.transaction
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.BANK_PAYMENT_IGNORED,
            targetType = AdminActionTargetType.BANK_TRANSACTION,
            targetId = id,
            summary = "Ignored bank payment of ${transaction.amount} from ${transaction.senderName}" +
                if (resolution.senderRemembered) " and every later one from this sender" else "",
            metadata = mapOf(
                "amount" to transaction.amount,
                "senderName" to transaction.senderName,
                "occurredAt" to transaction.occurredAt.toString(),
                "senderIgnored" to resolution.senderRemembered
            )
        )
        return ResponseEntity.ok(resolution)
    }

    @GetMapping("/senders")
    fun getSenders(): ResponseEntity<List<BankSenderResponse>> =
        ResponseEntity.ok(bankPaymentService.getSenders())

    @DeleteMapping("/senders/{id}")
    fun forgetSender(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val sender = bankPaymentService.forgetSender(id)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.BANK_SENDER_FORGOTTEN,
            targetType = AdminActionTargetType.BANK_SENDER,
            targetId = id,
            subscriberId = sender.subscriberId,
            summary = "Forgot bank sender ${sender.senderName}" +
                (sender.subscriberName?.let { " (paid for $it)" } ?: " (ignored)"),
            metadata = mapOf(
                "senderName" to sender.senderName,
                "subscriberId" to sender.subscriberId,
                "ignored" to sender.ignored
            )
        )
        return ResponseEntity.noContent().build()
    }
}
