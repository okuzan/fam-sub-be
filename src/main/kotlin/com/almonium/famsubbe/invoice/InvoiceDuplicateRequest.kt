package com.almonium.famsubbe.invoice

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class InvoiceDuplicateRequest(
    @field:NotNull
    val subscriberId: UUID,
    @field:DecimalMin(value = "0.01")
    val amount: BigDecimal? = null,
    val invoiceDate: LocalDate? = null,
    val notes: String? = null,
    val sendEmail: Boolean = false
)
