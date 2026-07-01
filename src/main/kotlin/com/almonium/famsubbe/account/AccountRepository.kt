package com.almonium.famsubbe.account

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByEmail(email: String): Account?

    @Query("""
        select count(a)
        from Account a
        join a.roles accountRole
        where accountRole = :role
    """)
    fun countByRole(role: Role): Long
}
