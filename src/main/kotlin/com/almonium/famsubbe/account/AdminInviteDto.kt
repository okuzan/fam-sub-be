package com.almonium.famsubbe.account

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.*

data class AdminInviteCreateRequest(
    @field:Email(message = "email must be valid")
    @field:NotBlank(message = "email is required")
    val email: String
)

data class AdminInviteAcceptRequest(
    @field:NotBlank(message = "token is required")
    val token: String
)

data class AdminInviteResponse(
    val id: UUID,
    val email: String,
    val status: AdminInviteStatus,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val revokedAt: Instant?,
    val invitedByAccountId: UUID,
    val acceptedByAccountId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class AdminInviteAcceptResponse(
    val accepted: Boolean,
    val email: String
)
