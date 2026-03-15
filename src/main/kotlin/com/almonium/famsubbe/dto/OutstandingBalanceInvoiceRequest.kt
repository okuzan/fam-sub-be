package com.almonium.famsubbe.dto

import java.util.*

data class OutstandingBalanceInvoiceRequest(
    val subscriberId: UUID,
    val sendEmail: Boolean = false,
    val notes: String? = null
)
