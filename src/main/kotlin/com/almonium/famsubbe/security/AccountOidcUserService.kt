package com.almonium.famsubbe.security

import com.almonium.famsubbe.service.AccountService
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

@Service
class AccountOidcUserService(
    private val accountService: AccountService
) : OAuth2UserService<OidcUserRequest, OidcUser> {

    private val delegate = OidcUserService()

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = delegate.loadUser(userRequest)

        val email = oidcUser.getAttribute<String>("email")
            ?.trim()
            ?.lowercase()
            ?: throw OAuth2AuthenticationException("Email is required")

        val account = accountService.findOrCreateGoogleSubscriber(email)

        val authorities = buildSet {
            addAll(oidcUser.authorities)
            account.roles.forEach { add(SimpleGrantedAuthority("ROLE_${it.name}")) }
        }

        val attributes = LinkedHashMap(oidcUser.attributes).apply {
            put("local_account_id", account.id?.toString())
            put("local_account_email", account.email)
        }

        return DefaultOidcUser(
            authorities,
            oidcUser.idToken,
            OidcUserInfo(attributes),
            "email"
        )
    }
}