package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.ChargeCreateRequest
import com.almonium.famsubbe.dto.ChargePageResponse
import com.almonium.famsubbe.dto.ChargeResponse
import com.almonium.famsubbe.dto.ChargeUpdateRequest
import com.almonium.famsubbe.entity.AdminActionTargetType
import com.almonium.famsubbe.entity.AdminActionType
import com.almonium.famsubbe.service.AccountService
import com.almonium.famsubbe.service.AdminAuditLogService
import com.almonium.famsubbe.service.ChargeService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth
import java.util.*

@RestController
@RequestMapping("/admin/charges")
class AdminChargeController(
    private val chargeService: ChargeService,
    private val accountService: AccountService,
    private val adminAuditLogService: AdminAuditLogService
) {

    @PostMapping
    fun createCharge(
        @Valid @RequestBody request: ChargeCreateRequest,
        authentication: Authentication
    ): ResponseEntity<ChargeResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val charge = chargeService.createCharge(request)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.CHARGE_CREATED,
            targetType = AdminActionTargetType.CHARGE,
            targetId = charge.id,
            fromMonth = charge.chargeMonth,
            toMonth = charge.chargeMonth,
            summary = "Created charge for ${charge.subscriptionServiceName} in ${charge.chargeMonth}",
            metadata = mapOf(
                "subscriptionServiceId" to charge.subscriptionServiceId,
                "amount" to charge.amount
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(charge)
    }

    @PutMapping("/{id}")
    fun updateCharge(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ChargeUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<ChargeResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val charge = chargeService.updateCharge(id, request)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.CHARGE_UPDATED,
            targetType = AdminActionTargetType.CHARGE,
            targetId = id,
            fromMonth = charge.chargeMonth,
            toMonth = charge.chargeMonth,
            summary = "Updated charge for ${charge.subscriptionServiceName} in ${charge.chargeMonth}",
            metadata = mapOf("amount" to charge.amount)
        )
        return ResponseEntity.ok(charge)
    }

    @GetMapping("/{id}")
    fun getCharge(@PathVariable id: UUID): ResponseEntity<ChargeResponse> {
        val charge = chargeService.getCharge(id)
        return ResponseEntity.ok(charge)
    }

    @GetMapping("/service/{serviceId}")
    fun getChargesByService(
        @PathVariable serviceId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "6") size: Int
    ): ResponseEntity<ChargePageResponse> {
        require(page >= 0) { "Page must not be negative" }
        require(size in 1..50) { "Page size must be between 1 and 50" }
        val charges = chargeService.getChargesByService(serviceId, page, size)
        return ResponseEntity.ok(charges)
    }

    @GetMapping("/month/{yearMonth}")
    fun getChargesByMonth(@PathVariable yearMonth: YearMonth): ResponseEntity<List<ChargeResponse>> {
        val charges = chargeService.getChargesByMonth(yearMonth)
        return ResponseEntity.ok(charges)
    }

    @DeleteMapping("/{id}")
    fun deleteCharge(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val charge = chargeService.getCharge(id)
        chargeService.deleteCharge(id)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.CHARGE_DELETED,
            targetType = AdminActionTargetType.CHARGE,
            targetId = id,
            fromMonth = charge.chargeMonth,
            toMonth = charge.chargeMonth,
            summary = "Deleted charge for ${charge.subscriptionServiceName} in ${charge.chargeMonth}",
            metadata = mapOf("amount" to charge.amount)
        )
        return ResponseEntity.noContent().build()
    }
}
