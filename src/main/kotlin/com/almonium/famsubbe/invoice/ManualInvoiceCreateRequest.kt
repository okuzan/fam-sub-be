package com.almonium.famsubbe.invoice

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

data class ManualInvoiceCreateRequest(
    @field:NotNull
    var subscriberId: UUID,
    @field:NotNull
    @field:DecimalMin(value = "0.01")
    var amount: BigDecimal,
    @field:NotNull
    var invoiceDate: LocalDate,
    @field:NotBlank
    val notes: String,
    val sendEmail: Boolean = false
)
