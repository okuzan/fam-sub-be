package com.almonium.famsubbe.dto

import java.time.YearMonth

data class CostCalculationSuggestion(
    val lastCalculatedToMonth: YearMonth?,
    val suggestedFromMonth: YearMonth,
    val suggestedToMonth: YearMonth
)
