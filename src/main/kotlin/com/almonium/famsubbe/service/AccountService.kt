package com.almonium.famsubbe.service

import com.almonium.famsubbe.entity.Account
import com.almonium.famsubbe.entity.Role
import com.almonium.famsubbe.repository.AccountRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.*

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun createAdmin(email: String, password: String): Account {
        val account = Account().apply {
            this.email = email
            this.passwordHash = passwordEncoder.encode(password)
            this.roles = mutableSetOf(Role.ADMIN)
        }
        return accountRepository.save(account)
    }

    fun createSubscriber(email: String, password: String): Account {
        val account = Account().apply {
            this.email = email
            this.passwordHash = passwordEncoder.encode(password)
            this.roles = mutableSetOf(Role.SUBSCRIBER)
        }
        return accountRepository.save(account)
    }

    fun findByEmail(email: String): Account? {
        return accountRepository.findByEmail(email)
    }

    fun findById(id: UUID): Account? {
        return accountRepository.findById(id).orElse(null)
    }
}
