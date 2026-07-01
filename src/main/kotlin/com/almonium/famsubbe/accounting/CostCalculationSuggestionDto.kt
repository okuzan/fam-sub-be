package com.almonium.famsubbe.accounting

import java.time.YearMonth

data class CostCalculationSuggestion(
    val lastCalculatedToMonth: YearMonth?,
    val suggestedFromMonth: YearMonth,
    val suggestedToMonth: YearMonth
)
