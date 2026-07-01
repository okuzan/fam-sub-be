package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.RunRecoveryPreviewResponse
import com.almonium.famsubbe.dto.RunUndoRequest
import com.almonium.famsubbe.dto.RunUndoResponse
import com.almonium.famsubbe.account.AccountService
import com.almonium.famsubbe.service.RecoveryService
import com.almonium.famsubbe.util.AuthenticationUtil
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
@RequestMapping("/admin/recovery")
class AdminRecoveryController(
    private val recoveryService: RecoveryService,
    private val accountService: AccountService
) {
    @GetMapping("/cost-runs/{runId}/preview")
    fun previewCostRunUndo(
        @PathVariable runId: UUID
    ): ResponseEntity<RunRecoveryPreviewResponse> {
        return ResponseEntity.ok(recoveryService.previewCostRunUndo(runId))
    }

    @PostMapping("/cost-runs/{runId}/undo")
    fun undoCostRun(
        @PathVariable runId: UUID,
        @RequestBody(required = false) request: RunUndoRequest?,
        authentication: Authentication
    ): ResponseEntity<RunUndoResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        return ResponseEntity.ok(recoveryService.undoCostRun(runId, performedByAccountId, request?.reason))
    }

    @GetMapping("/invoice-runs/{runId}/preview")
    fun previewInvoiceRunUndo(
        @PathVariable runId: UUID
    ): ResponseEntity<RunRecoveryPreviewResponse> {
        return ResponseEntity.ok(recoveryService.previewInvoiceRunUndo(runId))
    }

    @PostMapping("/invoice-runs/{runId}/undo")
    fun undoInvoiceRun(
        @PathVariable runId: UUID,
        @RequestBody(required = false) request: RunUndoRequest?,
        authentication: Authentication
    ): ResponseEntity<RunUndoResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        return ResponseEntity.ok(recoveryService.undoInvoiceRun(runId, performedByAccountId, request?.reason))
    }
}
