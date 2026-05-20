package com.almonium.famsubbe.dto

import java.util.*

data class RunRecoveryPreviewResponse(
    val runId: UUID,
    val type: String,
    val allowed: Boolean,
    val alreadyUndone: Boolean,
    val blockers: List<String>,
    val effects: Map<String, Any>,
    val summary: String
)
