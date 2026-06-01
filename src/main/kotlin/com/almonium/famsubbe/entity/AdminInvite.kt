package com.almonium.famsubbe.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.*

@Entity
@Table(
    name = "admin_invite",
    indexes = [
        Index(name = "idx_admin_invite_email", columnList = "email"),
        Index(name = "idx_admin_invite_status", columnList = "status"),
        Index(name = "idx_admin_invite_token_hash", columnList = "token_hash")
    ]
)
class AdminInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(nullable = false)
    var email: String = ""

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    var tokenHash: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AdminInviteStatus = AdminInviteStatus.PENDING

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant? = null

    @Column(name = "accepted_at")
    var acceptedAt: Instant? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(name = "invited_by_account_id", nullable = false, updatable = false)
    var invitedByAccountId: UUID? = null

    @Column(name = "accepted_by_account_id")
    var acceptedByAccountId: UUID? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
