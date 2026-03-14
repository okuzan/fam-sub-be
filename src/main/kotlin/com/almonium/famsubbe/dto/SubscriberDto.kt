package com.almonium.famsubbe.dto

import java.util.*

data class SubscriberResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val createdAt: Date,
    val updatedAt: Date
)

data class SubscriberCreateRequest(
    val name: String,
    val email: String
)

data class SubscriberUpdateRequest(
    val name: String,
    val email: String
)
