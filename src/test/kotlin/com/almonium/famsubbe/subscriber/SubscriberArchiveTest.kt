package com.almonium.famsubbe.subscriber

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriberArchiveTest {
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)

    @Test
    fun `a former member with nothing owed and no credit is archived`() {
        assertTrue(SubscriberService.isArchived(listOf(september), false, BigDecimal.ZERO, october))
        assertTrue(SubscriberService.isArchived(emptyList(), false, BigDecimal("0.00"), october))
    }

    @Test
    fun `a membership running this month or open-ended keeps them active`() {
        assertFalse(SubscriberService.isArchived(listOf(september, october), false, BigDecimal.ZERO, october))
        assertFalse(SubscriberService.isArchived(listOf(september, null), false, BigDecimal.ZERO, october))
    }

    @Test
    fun `debt or credit keeps a former member active`() {
        assertFalse(SubscriberService.isArchived(listOf(september), true, BigDecimal.ZERO, october))
        assertFalse(SubscriberService.isArchived(listOf(september), false, BigDecimal("12.50"), october))
    }
}
