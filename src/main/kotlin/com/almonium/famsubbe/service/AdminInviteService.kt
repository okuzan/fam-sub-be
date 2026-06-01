package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.AdminInviteAcceptResponse
import com.almonium.famsubbe.dto.AdminInviteCreateRequest
import com.almonium.famsubbe.dto.AdminInviteResponse
import com.almonium.famsubbe.entity.Account
import com.almonium.famsubbe.entity.AdminInvite
import com.almonium.famsubbe.entity.AdminInviteStatus
import com.almonium.famsubbe.entity.Role
import com.almonium.famsubbe.repository.AccountRepository
import com.almonium.famsubbe.repository.AdminInviteRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.*

@Service
@Transactional
class AdminInviteService(
    private val adminInviteRepository: AdminInviteRepository,
    private val accountRepository: AccountRepository,
    private val adminInviteEmailService: AdminInviteEmailService,
    @Value($$"${app.web-domain}") private val webDomain: String
) {
    private val secureRandom = SecureRandom()

    fun createInvite(request: AdminInviteCreateRequest, invitedByAccountId: UUID): AdminInviteResponse {
        val email = request.email.trim().lowercase()
        val existingAccount = accountRepository.findByEmail(email)
        if (existingAccount != null && Role.ADMIN in existingAccount.roles) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Account is already an admin")
        }
        if (hasActivePendingInvite(email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A pending admin invite already exists for this email")
        }

        val now = Instant.now()
        val rawToken = generateToken()
        val invite = adminInviteRepository.save(
            AdminInvite().apply {
                this.email = email
                this.tokenHash = hashToken(rawToken)
                this.status = AdminInviteStatus.PENDING
                this.expiresAt = now.plus(Duration.ofDays(7))
                this.invitedByAccountId = invitedByAccountId
                this.createdAt = now
                this.updatedAt = now
            }
        )

        val inviteUrl = "$webDomain/admin-invites/accept?token=$rawToken"
        val sent = adminInviteEmailService.sendInvite(email, inviteUrl)
        if (!sent) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send admin invite email")
        }

        return invite.toResponse()
    }

    @Transactional(readOnly = true)
    fun getInvites(): List<AdminInviteResponse> =
        adminInviteRepository.findAllByOrderByCreatedAtDesc()
            .map { it.toResponse() }

    fun revokeInvite(inviteId: UUID): AdminInviteResponse {
        val invite = adminInviteRepository.findById(inviteId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Admin invite not found") }

        if (invite.status != AdminInviteStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending invites can be revoked")
        }

        invite.status = AdminInviteStatus.REVOKED
        val now = Instant.now()
        invite.revokedAt = now
        invite.updatedAt = now
        return adminInviteRepository.save(invite).toResponse()
    }

    fun acceptInvite(rawToken: String, account: Account): AdminInviteAcceptResponse {
        val invite = adminInviteRepository.findByTokenHash(hashToken(rawToken.trim()))
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Admin invite not found")

        if (invite.status != AdminInviteStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin invite is no longer pending")
        }

        if (requireNotNull(invite.expiresAt).isBefore(Instant.now())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin invite has expired")
        }

        if (!invite.email.equals(account.email, ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Sign in with the invited Google account")
        }

        if (Role.ADMIN !in account.roles) {
            account.roles.add(Role.ADMIN)
            accountRepository.save(account)
        }

        invite.status = AdminInviteStatus.ACCEPTED
        val now = Instant.now()
        invite.acceptedAt = now
        invite.acceptedByAccountId = account.id
        invite.updatedAt = now
        adminInviteRepository.save(invite)

        return AdminInviteAcceptResponse(
            accepted = true,
            email = account.email
        )
    }

    @Transactional(readOnly = true)
    fun hasActivePendingInvite(email: String): Boolean =
        adminInviteRepository.existsActiveByEmail(
            email.trim().lowercase(),
            AdminInviteStatus.PENDING,
            Instant.now()
        )

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun AdminInvite.toResponse(): AdminInviteResponse =
        AdminInviteResponse(
            id = requireNotNull(id),
            email = email,
            status = status,
            expiresAt = requireNotNull(expiresAt),
            acceptedAt = acceptedAt,
            revokedAt = revokedAt,
            invitedByAccountId = requireNotNull(invitedByAccountId),
            acceptedByAccountId = acceptedByAccountId,
            createdAt = requireNotNull(createdAt),
            updatedAt = requireNotNull(updatedAt)
        )
}
