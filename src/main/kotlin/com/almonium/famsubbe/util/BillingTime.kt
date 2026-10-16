package com.almonium.famsubbe.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The group lives in Kyiv, so "this month" and "today" are Kyiv dates. The
 * container clock is UTC, which would put the first hours of every month in the
 * previous one.
 */
object BillingTime {
    const val ZONE_ID = "Europe/Kyiv"
    val ZONE: ZoneId = ZoneId.of(ZONE_ID)

    fun currentMonth(): YearMonth = YearMonth.now(ZONE)

    fun dateOf(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()
}
