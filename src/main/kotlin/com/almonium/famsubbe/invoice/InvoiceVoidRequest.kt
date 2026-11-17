package com.almonium.famsubbe.invoice

import java.math.BigDecimal

data class InvoiceVoidRequest(
    val reason: String? = null
)

data class InvoiceVoidResult(
    val invoice: InvoiceResponse,
    /** Credited to the subscriber's balance because the voided invoice had been paid; null otherwise. */
    val balanceCredited: BigDecimal?
)
