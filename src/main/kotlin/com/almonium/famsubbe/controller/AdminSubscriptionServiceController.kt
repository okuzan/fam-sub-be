package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.SubscriptionServiceCreateRequest
import com.almonium.famsubbe.dto.SubscriptionServiceResponse
import com.almonium.famsubbe.dto.SubscriptionServiceUpdateRequest
import com.almonium.famsubbe.entity.AdminActionTargetType
import com.almonium.famsubbe.entity.AdminActionType
import com.almonium.famsubbe.service.AccountService
import com.almonium.famsubbe.service.AdminAuditLogService
import com.almonium.famsubbe.service.SubscriptionServiceService
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
import java.util.*

@RestController
@RequestMapping("/admin/subscription-services")
class AdminSubscriptionServiceController(
    private val subscriptionServiceService: SubscriptionServiceService,
    private val accountService: AccountService,
    private val adminAuditLogService: AdminAuditLogService
) {

    @GetMapping
    fun getAllServices(): ResponseEntity<List<SubscriptionServiceResponse>> {
        val services = subscriptionServiceService.getAllServices()
        return ResponseEntity.ok(services)
    }

    @GetMapping("/{id}")
    fun getServiceById(@PathVariable id: UUID): ResponseEntity<SubscriptionServiceResponse> {
        val service = subscriptionServiceService.getServiceById(id)
        return ResponseEntity.ok(service)
    }

    @PostMapping
    fun createService(
        @Valid @RequestBody request: SubscriptionServiceCreateRequest,
        authentication: Authentication
    ): ResponseEntity<SubscriptionServiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val service = subscriptionServiceService.createService(request)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.SUBSCRIPTION_SERVICE_CREATED,
            targetType = AdminActionTargetType.SUBSCRIPTION_SERVICE,
            targetId = service.id,
            summary = "Created subscription service ${service.name}",
            metadata = mapOf("price" to service.price)
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(service)
    }

    @PutMapping("/{id}")
    fun updateService(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SubscriptionServiceUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<SubscriptionServiceResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val service = subscriptionServiceService.updateService(id, request)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.SUBSCRIPTION_SERVICE_UPDATED,
            targetType = AdminActionTargetType.SUBSCRIPTION_SERVICE,
            targetId = id,
            summary = "Updated subscription service ${service.name}",
            metadata = mapOf("price" to service.price)
        )
        return ResponseEntity.ok(service)
    }

    @DeleteMapping("/{id}")
    fun deleteService(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val service = subscriptionServiceService.getServiceById(id)
        subscriptionServiceService.deleteService(id)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.SUBSCRIPTION_SERVICE_DELETED,
            targetType = AdminActionTargetType.SUBSCRIPTION_SERVICE,
            targetId = id,
            summary = "Deleted subscription service ${service.name}",
            metadata = mapOf("price" to service.price)
        )
        return ResponseEntity.noContent().build()
    }
}
