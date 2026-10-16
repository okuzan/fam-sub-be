package com.almonium.famsubbe.invoice

import java.math.BigDecimal

data class InvoiceStatusUpdateRequest(
    val status: String
)

data class InvoiceStatusChange(
    val invoice: InvoiceResponse,
    /** Credit returned to the subscriber because a balance-paid invoice was reopened. */
    val balanceRefunded: BigDecimal?
)
