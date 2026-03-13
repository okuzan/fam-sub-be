package com.almonium.famsubbe.dto

data class AuthMeResponse(
    val authenticated: Boolean,
    val accountId: String?,
    val email: String?,
    val roles: Set<String>,
    val scopes: Set<String>,
    val principalType: String
)
