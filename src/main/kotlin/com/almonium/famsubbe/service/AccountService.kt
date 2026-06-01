package com.almonium.famsubbe.service

import com.almonium.famsubbe.entity.Account
import com.almonium.famsubbe.entity.Role
import com.almonium.famsubbe.repository.AccountRepository
import com.almonium.famsubbe.repository.SubscriberRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.*

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val subscriberRepository: SubscriberRepository,
    private val adminInviteService: AdminInviteService,
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

    fun findOrCreateGoogleSubscriber(email: String): Account {
        val normalizedEmail = email.trim().lowercase()
        val existing = accountRepository.findByEmail(normalizedEmail)
        val subscriber = subscriberRepository.findByEmailIgnoreCase(normalizedEmail)
        val hasPendingAdminInvite = adminInviteService.hasActivePendingInvite(normalizedEmail)

        if (existing != null) {
            if (subscriber != null && Role.SUBSCRIBER !in existing.roles) {
                existing.roles.add(Role.SUBSCRIBER)
                return accountRepository.save(existing)
            }
            if (Role.ADMIN in existing.roles || subscriber != null || hasPendingAdminInvite) {
                return existing
            }
            throw SubscriberRegistrationNotAllowedException(normalizedEmail)
        }

        if (subscriber == null && !hasPendingAdminInvite) {
            throw SubscriberRegistrationNotAllowedException(normalizedEmail)
        }

        val account = Account().apply {
            this.email = normalizedEmail
            this.passwordHash = passwordEncoder.encode(UUID.randomUUID().toString())
            this.roles = if (subscriber == null) mutableSetOf() else mutableSetOf(Role.SUBSCRIBER)
        }
        return accountRepository.save(account)
    }
}

class SubscriberRegistrationNotAllowedException(email: String) :
    RuntimeException("No subscriber is registered for Google account email: $email")
