package com.almonium.famsubbe.credit

import com.almonium.famsubbe.account.AccountService
import com.almonium.famsubbe.admin.AdminActionTargetType
import com.almonium.famsubbe.admin.AdminActionType
import com.almonium.famsubbe.admin.AdminAuditLogService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/admin/credit-notes")
class AdminCreditNoteController(
    private val creditNoteService: CreditNoteService,
    private val accountService: AccountService,
    private val adminAuditLogService: AdminAuditLogService
) {
    @GetMapping
    fun getCreditNotes(@RequestParam(required = false) subscriberId: UUID?): ResponseEntity<List<CreditNoteResponse>> =
        ResponseEntity.ok(creditNoteService.getCreditNotes(subscriberId))

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreditNoteCreateRequest,
        authentication: Authentication
    ): ResponseEntity<CreditNoteCreateResult> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val result = creditNoteService.create(request, performedByAccountId)
        val note = result.creditNote
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.CREDIT_NOTE_CREATED,
            targetType = AdminActionTargetType.CREDIT_NOTE,
            targetId = note.id,
            subscriberId = note.subscriberId,
            summary = "Credited ₴${note.amount} to ${note.subscriberName}: ${note.reason}",
            metadata = mapOf(
                "amount" to note.amount,
                "occurredOn" to note.occurredOn.toString(),
                "reason" to note.reason,
                "settledInvoiceIds" to result.settledInvoiceIds,
                "settledAmount" to note.settledAmount,
                "balanceAfter" to result.balanceAfter,
                "sendEmail" to request.sendEmail
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @PostMapping("/{id}/undo")
    fun undo(@PathVariable id: UUID, authentication: Authentication): ResponseEntity<CreditNoteUndoResult> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val result = creditNoteService.undo(id, performedByAccountId)
        val note = result.creditNote
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.CREDIT_NOTE_UNDONE,
            targetType = AdminActionTargetType.CREDIT_NOTE,
            targetId = id,
            subscriberId = note.subscriberId,
            summary = "Undid ₴${note.amount} credit note for ${note.subscriberName}",
            metadata = mapOf(
                "amount" to note.amount,
                "reason" to note.reason,
                "reopenedInvoiceIds" to result.reopenedInvoiceIds,
                "balanceAfter" to result.balanceAfter
            )
        )
        return ResponseEntity.ok(result)
    }
}
