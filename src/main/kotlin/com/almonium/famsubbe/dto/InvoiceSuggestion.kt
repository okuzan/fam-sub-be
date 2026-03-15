package com.almonium.famsubbe.dto

import java.time.YearMonth

data class InvoiceSuggestion(
    val lastInvoicedToMonth: YearMonth?,
    val suggestedFromMonth: YearMonth?,
    val suggestedToMonth: YearMonth?
)