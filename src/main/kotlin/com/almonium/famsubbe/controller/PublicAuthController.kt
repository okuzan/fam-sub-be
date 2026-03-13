package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.AuthMeResponse
import com.almonium.famsubbe.security.AccountPrincipal
import com.almonium.famsubbe.service.AccountService
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/public/auth")
class PublicAuthController(
    private val accountService: AccountService
) {

    @GetMapping("/me")
    fun me(authentication: Authentication?): AuthMeResponse {
        val principal = authentication?.principal
        if (authentication == null || !authentication.isAuthenticated || principal == null || principal == "anonymousUser") {
            return AuthMeResponse(
                authenticated = false,
                accountId = null,
                email = null,
                roles = emptySet(),
                scopes = emptySet(),
                principalType = "anonymous"
            )
        }

        val authorities = authentication.authorities.mapNotNull { it.authority }.toSet()

        val roles = authorities
            .filter { it.startsWith("ROLE_") }
            .map { it.removePrefix("ROLE_") }
            .toSet()

        val scopes = authorities
            .filter { it.startsWith("SCOPE_") }
            .map { it.removePrefix("SCOPE_") }
            .toSet()

        val account = when (principal) {
            is AccountPrincipal -> accountService.findByEmail(principal.username)
            is OAuth2User -> resolveAccountFromOAuth2Principal(principal)
            else -> null
        }

        return AuthMeResponse(
            authenticated = true,
            accountId = account?.id?.toString(),
            email = account?.email,
            roles = roles,
            scopes = scopes,
            principalType = principal.javaClass.simpleName
        )
    }

    private fun resolveAccountFromOAuth2Principal(principal: OAuth2User) =
        principal.getAttribute<String>("local_account_email")
            ?.let { accountService.findByEmail(it) }
            ?: principal.getAttribute<String>("email")
                ?.let { accountService.findByEmail(it.trim().lowercase()) }
}
