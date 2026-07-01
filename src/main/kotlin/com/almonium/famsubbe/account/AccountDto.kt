package com.almonium.famsubbe.account

import java.util.*

data class AccountResponse(
    val id: UUID,
    val email: String,
    val roles: Set<String>,
    val createdAt: Date?,
    val updatedAt: Date?,
    val canDelete: Boolean
)
