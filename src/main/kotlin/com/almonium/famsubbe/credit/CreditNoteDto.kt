package com.almonium.famsubbe.credit

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

data class CreditNoteCreateRequest(
    @field:NotNull(message = "subscriberId is required")
    val subscriberId: UUID?,
    @field:NotNull(message = "amount is required")
    @field:DecimalMin(value = "0.01", message = "amount must be positive")
    @field:Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
    val amount: BigDecimal?,
    /** The day they paid; today in Kyiv when left out. */
    val occurredOn: LocalDate? = null,
    @field:NotBlank(message = "reason is required")
    @field:Size(max = 2000, message = "reason must be at most 2000 characters")
    val reason: String?,
    /** Email them what it was and what it paid. On by default. */
    val sendEmail: Boolean = true
)

data class CreditNoteResponse(
    val id: UUID,
    val subscriberId: UUID,
    val subscriberName: String,
    val amount: BigDecimal,
    val occurredOn: LocalDate,
    val reason: String,
    val createdAt: Instant,
    val createdByAccountId: UUID,
    val settledInvoiceCount: Int,
    val settledAmount: BigDecimal,
    val emailSentAt: Instant?,
    val undoneAt: Instant?,
    val undoneByAccountId: UUID?
)

data class CreditNoteCreateResult(
    val creditNote: CreditNoteResponse,
    val settledInvoiceIds: List<UUID>,
    val balanceAfter: BigDecimal
)

data class CreditNoteUndoResult(
    val creditNote: CreditNoteResponse,
    val reopenedInvoiceIds: List<UUID>,
    val balanceAfter: BigDecimal
)
