package com.almonium.famsubbe.invoice

import java.math.BigDecimal
import java.util.*

data class IncomingPaymentResult(
    val settledInvoiceIds: List<UUID>,
    val settledAmount: BigDecimal,
    val balanceAfter: BigDecimal
)
