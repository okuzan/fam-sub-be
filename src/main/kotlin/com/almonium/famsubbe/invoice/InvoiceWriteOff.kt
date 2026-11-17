package com.almonium.famsubbe.invoice

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.math.BigDecimal
import java.util.UUID

data class InvoiceWriteOffRequest(
    @field:NotEmpty
    val ledgerEntryIds: Set<UUID>,
    @field:NotBlank
    val reason: String
)

data class InvoiceWriteOffResult(
    /** The original invoice, now VOID, still holding the written-off lines. */
    val voidedInvoice: InvoiceResponse,
    /** The same period without those lines; null when every line was written off. */
    val reissuedInvoice: InvoiceResponse?,
    val writtenOffAmount: BigDecimal,
    /** e.g. "Claude November 2026". */
    val writtenOffLines: List<String>
)
