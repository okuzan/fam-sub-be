package com.almonium.famsubbe.payment

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The pay page behind the link in every email. Public, because paying should
 * not need a login: the token is unguessable, and the page reveals only a first
 * name and an amount.
 */
@RestController
@RequestMapping("/public/pay")
class PublicPayController(
    private val paymentDetailsService: PaymentDetailsService
) {
    @GetMapping("/{payToken}")
    fun getPayPage(@PathVariable payToken: String): ResponseEntity<PayPageResponse> =
        ResponseEntity.ok(paymentDetailsService.getPayPage(payToken))
}
