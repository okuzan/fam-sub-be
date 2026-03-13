package com.almonium.famsubbe.security

import com.almonium.famsubbe.repository.AccountRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AccountUserDetailsService(
    private val accountRepository: AccountRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val account = accountRepository.findByEmail(username)
            ?: throw UsernameNotFoundException("Account not found: $username")

        return AccountPrincipal(account)
    }
}
