package com.almonium.famsubbe.account

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
interface AdminInviteRepository : JpaRepository<AdminInvite, UUID> {
    fun findByTokenHash(tokenHash: String): AdminInvite?

    fun findAllByOrderByCreatedAtDesc(): List<AdminInvite>

    @Query(
        """
        select count(ai) > 0
        from AdminInvite ai
        where lower(ai.email) = lower(:email)
          and ai.status = :status
          and ai.expiresAt > :now
        """
    )
    fun existsActiveByEmail(email: String, status: AdminInviteStatus, now: Instant): Boolean
}
