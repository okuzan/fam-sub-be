package com.almonium.famsubbe.bank

import com.almonium.famsubbe.account.AccountService
import com.almonium.famsubbe.admin.AdminActionTargetType
import com.almonium.famsubbe.admin.AdminActionType
import com.almonium.famsubbe.admin.AdminAuditLogService
import com.almonium.famsubbe.util.AuthenticationUtil
import jakarta.validation.Valid
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
import java.util.*

/** Subscription bills paid from the card: the ones waiting for review, and the rules that recognise them. */
@RestController
@RequestMapping("/admin/bank-charges")
class AdminBankChargeController(
    private val bankChargeService: BankChargeService,
    private val accountService: AccountService,
    private val adminAuditLogService: AdminAuditLogService
) {
    @GetMapping
    fun getBankCharges(
        @RequestParam(required = false) status: BankDebitStatus?,
        @RequestParam(defaultValue = "200") limit: Int
    ): ResponseEntity<BankChargesOverview> =
        ResponseEntity.ok(bankChargeService.getOverview(status, limit))

    @PostMapping("/{id}/charge")
    fun charge(
        @PathVariable id: UUID,
        @RequestBody request: BankDebitChargeRequest,
        authentication: Authentication
    ): ResponseEntity<BankDebitResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val debit = bankChargeService.charge(id, request, performedByAccountId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.BANK_CHARGE_APPLIED,
            targetType = AdminActionTargetType.BANK_DEBIT,
            targetId = id,
            fromMonth = debit.chargeMonth,
            toMonth = debit.chargeMonth,
            summary = "Charged card payment of ${debit.amount} (${debit.originalAmount} ${debit.currency}) " +
                "to ${debit.subscriptionServiceName} for ${debit.chargeMonth}",
            metadata = mapOf(
                "amount" to debit.amount,
                "originalAmount" to debit.originalAmount,
                "currency" to debit.currency,
                "merchant" to debit.merchant,
                "occurredAt" to debit.occurredAt.toString(),
                "subscriptionServiceId" to debit.subscriptionServiceId,
                "chargeId" to debit.chargeId,
                "amountRemembered" to request.rememberAmount
            )
        )
        return ResponseEntity.ok(debit)
    }

    @PostMapping("/{id}/ignore")
    fun ignore(
        @PathVariable id: UUID,
        @RequestBody request: BankDebitIgnoreRequest,
        authentication: Authentication
    ): ResponseEntity<BankDebitResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val debit = bankChargeService.ignore(id, request, performedByAccountId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.BANK_CHARGE_IGNORED,
            targetType = AdminActionTargetType.BANK_DEBIT,
            targetId = id,
            summary = "Ignored card payment of ${debit.amount} (${debit.originalAmount} ${debit.currency}) " +
                "to ${debit.description ?: debit.merchant}" +
                if (request.rememberAmount) " and every later one of the same amount" else "",
            metadata = mapOf(
                "amount" to debit.amount,
                "originalAmount" to debit.originalAmount,
                "currency" to debit.currency,
                "merchant" to debit.merchant,
                "occurredAt" to debit.occurredAt.toString(),
                "amountRemembered" to request.rememberAmount
            )
        )
        return ResponseEntity.ok(debit)
    }

    @PostMapping("/{id}/undo")
    fun undo(@PathVariable id: UUID, authentication: Authentication): ResponseEntity<BankDebitUndoResult> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val result = bankChargeService.undo(id)
        val debit = result.debit
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.BANK_CHARGE_UNDONE,
            targetType = AdminActionTargetType.BANK_DEBIT,
            targetId = id,
            summary = "Took card payment of ${debit.amount} (${debit.originalAmount} ${debit.currency}) " +
                "out of its charge" + if (result.chargeDeleted) ", deleting the charge" else "",
            metadata = mapOf(
                "amount" to debit.amount,
                "merchant" to debit.merchant,
                "chargeId" to result.chargeId,
                "chargeDeleted" to result.chargeDeleted,
                "chargeAmountAfter" to result.chargeAmountAfter
            )
        )
        return ResponseEntity.ok(result)
    }

    @PostMapping("/rules")
    fun createRule(
        @Valid @RequestBody request: BankMerchantRuleCreateRequest,
        authentication: Authentication
    ): ResponseEntity<BankMerchantRuleResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val rule = bankChargeService.createRule(request, performedByAccountId)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.BANK_CHARGE_RULE_CREATED,
            targetType = AdminActionTargetType.BANK_MERCHANT_RULE,
            targetId = rule.id,
            summary = "Added card rule: ${rule.merchant} ${rule.amount} ${rule.currency} → " +
                (rule.subscriptionServiceName ?: "ignore"),
            metadata = mapOf(
                "merchant" to rule.merchant,
                "amount" to rule.amount,
                "currency" to rule.currency,
                "subscriptionServiceId" to rule.subscriptionServiceId
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(rule)
    }

    @DeleteMapping("/rules/{id}")
    fun deleteRule(@PathVariable id: UUID, authentication: Authentication): ResponseEntity<Void> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val rule = bankChargeService.deleteRule(id)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.BANK_CHARGE_RULE_DELETED,
            targetType = AdminActionTargetType.BANK_MERCHANT_RULE,
            targetId = id,
            summary = "Removed card rule: ${rule.merchant} ${rule.amount} ${rule.currency} → " +
                (rule.subscriptionServiceName ?: "ignore"),
            metadata = mapOf(
                "merchant" to rule.merchant,
                "amount" to rule.amount,
                "currency" to rule.currency,
                "subscriptionServiceId" to rule.subscriptionServiceId
            )
        )
        return ResponseEntity.noContent().build()
    }
}
