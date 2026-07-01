package com.almonium.famsubbe.invoice

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class InvoiceDuplicateRequest(
    @field:NotNull
    val subscriberId: UUID,
    val sendEmail: Boolean = false
)
