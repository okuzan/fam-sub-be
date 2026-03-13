package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.Account
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByEmail(email: String): Account?
}
