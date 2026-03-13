package com.almonium.famsubbe.security

import com.almonium.famsubbe.service.AccountService
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import java.util.LinkedHashMap

@Service
class AccountOAuth2UserService(
    private val accountService: AccountService
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val delegate = DefaultOAuth2UserService()

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oauthUser = delegate.loadUser(userRequest)
        val email = oauthUser.getAttribute<String>("email")
            ?: throw OAuth2AuthenticationException(
                OAuth2Error("invalid_user_info"),
                "Google account email is required"
            )

        val account = accountService.findOrCreateGoogleSubscriber(email)
        val authorities = account.roles
            .map { SimpleGrantedAuthority("ROLE_${it.name}") }
            .toSet()

        val userNameAttributeName = userRequest.clientRegistration
            .providerDetails
            .userInfoEndpoint
            .userNameAttributeName
            .ifBlank { "email" }

        val attributes = LinkedHashMap(oauthUser.attributes).apply {
            put("local_account_id", account.id?.toString())
            put("local_account_email", account.email)
        }

        return DefaultOAuth2User(authorities, attributes, userNameAttributeName)
    }
}
