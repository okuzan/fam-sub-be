package com.almonium.famsubbe.invoice

import java.time.YearMonth

data class InvoiceSuggestion(
    val lastInvoicedToMonth: YearMonth?,
    val suggestedFromMonth: YearMonth?,
    val suggestedToMonth: YearMonth?
)