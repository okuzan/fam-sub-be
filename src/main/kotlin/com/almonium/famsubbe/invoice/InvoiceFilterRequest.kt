package com.almonium.famsubbe.invoice

import java.time.Instant
import java.util.*

data class InvoiceFilterRequest(
    val subscriberId: UUID? = null,
    val status: String? = null,
    val dateFrom: Instant? = null,
    val dateTo: Instant? = null,
    val origin: String? = null
)
