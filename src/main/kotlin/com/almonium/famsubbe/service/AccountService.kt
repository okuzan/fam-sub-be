package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.AccountResponse
import com.almonium.famsubbe.entity.Account
import com.almonium.famsubbe.entity.Role
import com.almonium.famsubbe.repository.AccountRepository
import com.almonium.famsubbe.repository.SubscriberRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
@Transactional
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

    @Transactional(readOnly = true)
    fun getAccounts(): List<AccountResponse> {
        val adminCount = accountRepository.countByRole(Role.ADMIN)
        return accountRepository.findAll()
            .sortedWith(compareBy<Account> { Role.ADMIN !in it.roles }.thenBy { it.email.lowercase() })
            .map { it.toResponse(adminCount) }
    }

    fun deleteAccount(id: UUID): AccountResponse {
        val account = accountRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found") }
        val adminCount = accountRepository.countByRole(Role.ADMIN)

        if (Role.ADMIN in account.roles && adminCount <= 1) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete the only admin account")
        }

        val response = account.toResponse(adminCount)
        accountRepository.delete(account)
        return response
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

    private fun Account.toResponse(adminCount: Long): AccountResponse {
        return AccountResponse(
            id = requireNotNull(id),
            email = email,
            roles = roles.map { it.name }.toSet(),
            createdAt = createdAt,
            updatedAt = updatedAt,
            canDelete = Role.ADMIN !in roles || adminCount > 1
        )
    }
}

class SubscriberRegistrationNotAllowedException(email: String) :
    RuntimeException("No subscriber is registered for Google account email: $email")
