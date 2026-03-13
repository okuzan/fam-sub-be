package com.almonium.famsubbe.security

import com.almonium.famsubbe.entity.Account
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class AccountPrincipal(
    private val account: Account
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return account.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }
    }

    override fun getPassword(): String? = account.passwordHash

    override fun getUsername(): String = account.email

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true
}
