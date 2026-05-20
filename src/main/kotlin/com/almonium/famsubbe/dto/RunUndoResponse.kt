package com.almonium.famsubbe.dto

import java.time.Instant
import java.util.*

data class RunUndoResponse(
    val runId: UUID,
    val type: String,
    val undoneAt: Instant,
    val summary: String,
    val effects: Map<String, Any>
)
