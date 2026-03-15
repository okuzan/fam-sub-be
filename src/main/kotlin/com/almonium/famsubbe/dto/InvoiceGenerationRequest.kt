package com.almonium.famsubbe.dto

import java.time.YearMonth
import java.util.*

data class InvoiceGenerationRequest(
    val fromMonth: YearMonth,
    val toMonth: YearMonth,
    val subscriberId: UUID?,
    val sendEmail: Boolean = false
)