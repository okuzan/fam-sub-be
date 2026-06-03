package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.SubscriberCreateRequest
import com.almonium.famsubbe.dto.SubscriberDebtPaymentResult
import com.almonium.famsubbe.dto.SubscriberDetailResponse
import com.almonium.famsubbe.dto.SubscriberResponse
import com.almonium.famsubbe.dto.SubscriberUpdateRequest
import com.almonium.famsubbe.entity.AdminActionTargetType
import com.almonium.famsubbe.entity.AdminActionType
import com.almonium.famsubbe.service.AccountService
import com.almonium.famsubbe.service.AdminAuditLogService
import com.almonium.famsubbe.service.InvoiceEmailService
import com.almonium.famsubbe.service.InvoiceService
import com.almonium.famsubbe.service.PinnedPostService
import com.almonium.famsubbe.service.SubscriberService
import com.almonium.famsubbe.util.AuthenticationUtil
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.*

@RestController
@RequestMapping("/admin/subscribers")
class AdminSubscriberController(
    private val subscriberService: SubscriberService,
    private val invoiceService: InvoiceService,
    private val invoiceEmailService: InvoiceEmailService,
    private val pinnedPostService: PinnedPostService,
    private val accountService: AccountService,
    private val adminAuditLogService: AdminAuditLogService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AdminSubscriberController::class.java)
    }

    @GetMapping
    fun getAllSubscribers(
        @RequestParam(required = false) namePrefix: String?
    ): ResponseEntity<List<SubscriberResponse>> {
        val subscribers = subscriberService.getAllSubscribers(namePrefix)
        return ResponseEntity.ok(subscribers)
    }

    @GetMapping("/debtors")
    fun getSubscribersWithDebt(): ResponseEntity<List<SubscriberResponse>> {
        val subscribers = subscriberService.getSubscribersWithDebt()
        return ResponseEntity.ok(subscribers)
    }

    @GetMapping("/{id}")
    fun getSubscriberById(@PathVariable id: UUID): ResponseEntity<SubscriberResponse> {
        val subscriber = subscriberService.getSubscriberById(id)
        return ResponseEntity.ok(subscriber)
    }

    @GetMapping("/{id}/details")
    fun getSubscriberDetails(@PathVariable id: UUID): ResponseEntity<SubscriberDetailResponse> {
        val subscriberDetails = invoiceService.getSubscriberDetails(id)
        return ResponseEntity.ok(subscriberDetails)
    }

    @PostMapping("/{id}/pay-off-debt")
    fun payOffSubscriberDebt(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "true") includeCredit: Boolean,
        authentication: Authentication
    ): ResponseEntity<SubscriberDebtPaymentResult> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val result = invoiceService.payOffSubscriberDebt(id, includeCredit)
        val details = invoiceService.getSubscriberDetails(id)
        val debtPaidEmailSent = if (result.paidCount > 0 && details.email.isNotBlank()) {
            invoiceEmailService.sendDebtPaidEmail(
                toEmail = details.email,
                subscriberName = result.subscriberName,
                paidInvoicesCount = result.paidCount,
                totalPaidAmount = result.totalPaidAmount,
                balanceAfter = result.balanceAfter,
                creditWrittenOff = result.creditWrittenOff
            )
        } else {
            false
        }

        if (result.paidCount > 0 && details.email.isNotBlank() && !debtPaidEmailSent) {
            logger.warn("Failed to send debt paid email to subscriber {}", id)
        }

        val creditSummary = if (result.creditWrittenOff > BigDecimal.ZERO) {
            " and wrote off credit ${result.creditWrittenOff}"
        } else {
            ""
        }
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.SUBSCRIBER_DEBT_PAID,
            targetType = AdminActionTargetType.SUBSCRIBER,
            targetId = id,
            subscriberId = id,
            summary = "Paid off ${result.paidCount} pending invoices for ${result.subscriberName}$creditSummary",
            metadata = mapOf(
                "attemptedCount" to result.attemptedCount,
                "paidCount" to result.paidCount,
                "totalPaidAmount" to result.totalPaidAmount,
                "includeCredit" to result.includeCredit,
                "balanceBefore" to result.balanceBefore,
                "balanceAfter" to result.balanceAfter,
                "creditWrittenOff" to result.creditWrittenOff,
                "debtPaidEmailSent" to debtPaidEmailSent,
                "invoiceIds" to result.items.map { it.invoiceId }
            )
        )
        return ResponseEntity.ok(result)
    }

    @PostMapping("/{id}/email-situation")
    fun emailSubscriberSituation(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val details = invoiceService.getSubscriberDetails(id)
        
        if (details.email.isEmpty()) {
            throw IllegalArgumentException("Subscriber has no email address")
        }

        val unpaidInvoices = invoiceService.buildSituationEmailInvoices(details.unpaidInvoices)
        val success = invoiceEmailService.sendSituationEmail(
            toEmail = details.email,
            subscriberName = details.name,
            totalOwed = details.totalAmountOwed,
            subscriberBalance = details.balance,
            activeSubscriptions = details.activeSubscriptions,
            unpaidInvoices = unpaidInvoices
        )

        return if (success) {
            adminAuditLogService.log(
                createdByAccountId = performedByAccountId,
                actionType = AdminActionType.SUBSCRIBER_SITUATION_EMAIL_SENT,
                targetType = AdminActionTargetType.SUBSCRIBER,
                targetId = id,
                subscriberId = id,
                summary = "Sent situation email to ${details.name}",
                metadata = mapOf(
                    "email" to details.email,
                    "totalOwed" to details.totalAmountOwed,
                    "unpaidInvoicesCount" to details.unpaidInvoices.size
                )
            )
            ResponseEntity.ok(
                mapOf("message" to "Situation email sent successfully to ${details.email}")
            )
        } else {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send email")
        }
    }

    @PostMapping
    fun createSubscriber(
        @Valid @RequestBody request: SubscriberCreateRequest,
        authentication: Authentication
    ): ResponseEntity<SubscriberResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val subscriber = subscriberService.createSubscriber(request)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.SUBSCRIBER_CREATED,
            targetType = AdminActionTargetType.SUBSCRIBER,
            targetId = subscriber.id,
            subscriberId = subscriber.id,
            summary = "Created subscriber ${subscriber.name}",
            metadata = mapOf(
                "email" to subscriber.email,
                "balance" to subscriber.balance,
                "autoPayInvoices" to subscriber.autoPayInvoices
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriber)
    }

    @PutMapping("/{id}")
    fun updateSubscriber(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SubscriberUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<SubscriberResponse> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val subscriber = subscriberService.updateSubscriber(id, request)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.SUBSCRIBER_UPDATED,
            targetType = AdminActionTargetType.SUBSCRIBER,
            targetId = id,
            subscriberId = id,
            summary = "Updated subscriber ${subscriber.name}",
            metadata = mapOf(
                "email" to subscriber.email,
                "balance" to subscriber.balance,
                "autoPayInvoices" to subscriber.autoPayInvoices
            )
        )
        return ResponseEntity.ok(subscriber)
    }

    @DeleteMapping("/{id}")
    fun deleteSubscriber(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val subscriber = subscriberService.getSubscriberById(id)
        subscriberService.deleteSubscriber(id)
        adminAuditLogService.log(
            createdByAccountId = performedByAccountId,
            actionType = AdminActionType.SUBSCRIBER_DELETED,
            targetType = AdminActionTargetType.SUBSCRIBER,
            targetId = id,
            subscriberId = id,
            summary = "Deleted subscriber ${subscriber.name}",
            metadata = mapOf("email" to subscriber.email)
        )
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/generate-pinned-post")
    fun generatePinnedPost(authentication: Authentication): ResponseEntity<Map<String, String>> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        logger.info("Starting generatePinnedPost request")
        return try {
            logger.info("Calling pinnedPostService.generatePinnedPost()")
            val pinnedPost = pinnedPostService.generatePinnedPost()
            logger.info("Successfully generated pinned post with length: {}", pinnedPost.length)
            adminAuditLogService.log(
                createdByAccountId = performedByAccountId,
                actionType = AdminActionType.PINNED_POST_GENERATED,
                targetType = AdminActionTargetType.PINNED_POST,
                summary = "Generated pinned post",
                metadata = mapOf("contentLength" to pinnedPost.length)
            )
            ResponseEntity.ok(mapOf("content" to pinnedPost))
        } catch (e: Exception) {
            logger.error("Failed to generate pinned post", e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate pinned post")
        }
    }
}
