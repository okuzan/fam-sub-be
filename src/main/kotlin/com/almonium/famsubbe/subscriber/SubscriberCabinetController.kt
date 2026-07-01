package com.almonium.famsubbe.subscriber

import com.almonium.famsubbe.account.AccountService
import com.almonium.famsubbe.util.AuthenticationUtil
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/subscriber")
class SubscriberCabinetController(
    private val subscriberCabinetService: SubscriberCabinetService,
    private val accountService: AccountService
) {

    @GetMapping("/cabinet")
    fun getCabinet(authentication: Authentication): ResponseEntity<SubscriberCabinetResponse> {
        val email = resolveEmail(authentication)
        return ResponseEntity.ok(subscriberCabinetService.getCabinet(email))
    }

    @GetMapping("/invoices")
    fun getInvoices(authentication: Authentication): ResponseEntity<List<SubscriberInvoiceSummaryResponse>> {
        val email = resolveEmail(authentication)
        return ResponseEntity.ok(subscriberCabinetService.getInvoices(email))
    }

    @GetMapping("/invoices/{invoiceId}")
    fun getInvoice(
        @PathVariable invoiceId: UUID,
        authentication: Authentication
    ): ResponseEntity<SubscriberInvoiceDetailResponse> {
        val email = resolveEmail(authentication)
        return ResponseEntity.ok(subscriberCabinetService.getInvoice(email, invoiceId))
    }

    private fun resolveEmail(authentication: Authentication): String =
        AuthenticationUtil.resolveAccount(authentication, accountService)?.email
            ?: throw IllegalStateException("Authenticated account not found")
}
