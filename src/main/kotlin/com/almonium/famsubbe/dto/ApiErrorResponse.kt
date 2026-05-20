package com.almonium.famsubbe.dto

import java.time.Instant

data class ApiErrorResponse(
    val message: String,
    val error: String = message,
    val status: Int,
    val timestamp: Instant = Instant.now(),
    val path: String? = null
)
