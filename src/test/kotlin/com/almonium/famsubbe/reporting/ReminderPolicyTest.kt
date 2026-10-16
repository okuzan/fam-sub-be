package com.almonium.famsubbe.reporting

import com.almonium.famsubbe.subscriber.UnpaidInvoiceDto
import com.almonium.famsubbe.util.BillingTime
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReminderPolicyTest {
    private val policy = ReminderPolicy(BigDecimal("200"))

    private val firstSaturday = LocalDate.of(2026, 11, 7)
    private val laterSaturday = LocalDate.of(2026, 11, 14)

    @Test
    fun `a large debt is reminded every Saturday`() {
        assertNull(policy.skipReason(BigDecimal("450.00"), listOf(invoiceCreated(laterSaturday.minusDays(20))), laterSaturday))
    }

    @Test
    fun `a small debt is reminded only on the first Saturday of the month`() {
        val invoices = listOf(invoiceCreated(LocalDate.of(2026, 10, 1)))

        assertNull(policy.skipReason(BigDecimal("31.93"), invoices, firstSaturday))
        assertNotNull(policy.skipReason(BigDecimal("31.93"), invoices, laterSaturday))
    }

    @Test
    fun `no reminder within a week of an invoice email`() {
        val invoices = listOf(invoiceCreated(firstSaturday.minusDays(2)), invoiceCreated(LocalDate.of(2026, 9, 1)))

        assertNotNull(policy.skipReason(BigDecimal("900.00"), invoices, firstSaturday))
        assertNull(policy.skipReason(BigDecimal("900.00"), invoices, firstSaturday.plusDays(7)))
    }

    @Test
    fun `nothing owed means no reminder`() {
        assertNotNull(policy.skipReason(BigDecimal("-5.00"), listOf(invoiceCreated(LocalDate.of(2026, 9, 1))), firstSaturday))
    }

    private fun invoiceCreated(date: LocalDate) = UnpaidInvoiceDto(
        id = UUID.randomUUID(),
        totalAmount = BigDecimal("31.93"),
        fromMonth = "2026-09",
        toMonth = "2026-09",
        invoiceDate = date,
        createdAt = date.atTime(12, 0).atZone(BillingTime.ZONE).toInstant(),
        status = "SENT",
        notes = null,
        origin = "SUBSCRIPTION_LEDGER"
    )
}
