package com.almonium.famsubbe.util

import com.almonium.famsubbe.entity.Account
import com.almonium.famsubbe.security.AccountPrincipal
import com.almonium.famsubbe.service.AccountService
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import java.util.UUID

object AuthenticationUtil {
    
    fun resolveAccount(authentication: Authentication, accountService: AccountService): Account? {
        return when (val principal = authentication.principal) {
            is AccountPrincipal -> accountService.findByEmail(principal.username)
            is OAuth2User -> resolveAccountFromOAuth2Principal(principal, accountService)
            else -> null
        }
    }
    
    fun resolveAccountId(authentication: Authentication, accountService: AccountService): UUID {
        val principal = authentication.principal
        val email = when (principal) {
            is AccountPrincipal -> principal.username
            is OAuth2User -> principal.getAttribute<String>("local_account_email")
                ?: principal.getAttribute<String>("email")
            else -> null
        }?.trim()?.lowercase() ?: throw IllegalStateException("Cannot resolve authenticated account")

        return accountService.findByEmail(email)?.id
            ?: throw IllegalStateException("Authenticated account not found")
    }
    
    private fun resolveAccountFromOAuth2Principal(principal: OAuth2User, accountService: AccountService): Account? =
        principal.getAttribute<String>("local_account_email")
            ?.let { accountService.findByEmail(it) }
            ?: principal.getAttribute<String>("email")
                ?.let { accountService.findByEmail(it.trim().lowercase()) }
}
