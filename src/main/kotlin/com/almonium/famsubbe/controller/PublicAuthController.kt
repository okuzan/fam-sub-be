package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.AuthMeResponse
import com.almonium.famsubbe.service.AccountService
import com.almonium.famsubbe.util.AuthenticationUtil
import org.springframework.security.core.Authentication
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

        val account = AuthenticationUtil.resolveAccount(authentication, accountService)

        return AuthMeResponse(
            authenticated = true,
            accountId = account?.id?.toString(),
            email = account?.email,
            roles = roles,
            scopes = scopes,
            principalType = principal.javaClass.simpleName
        )
    }
}
