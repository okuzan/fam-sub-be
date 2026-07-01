package com.almonium.famsubbe.finance

import com.almonium.famsubbe.invoice.Invoice
import com.almonium.famsubbe.invoice.InvoiceStatus
import com.almonium.famsubbe.subscriber.Subscriber
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FinanceSummaryCalculatorTest {
    @Test
    fun `calculates net debt per subscriber before aggregating`() {
        val now = Instant.parse("2026-07-01T12:00:00Z")
        val alice = subscriber("Alice", "40.00")
        val bob = subscriber("Bob", "150.00")
        val carol = subscriber("Carol", "20.00")
        val oldest = invoice(alice, "100.00", "2026-06-01T12:00:00Z")
        val invoices = listOf(
            oldest,
            invoice(alice, "20.00", "2026-06-15T12:00:00Z"),
            invoice(bob, "100.00", "2026-06-20T12:00:00Z")
        )

        val result = FinanceSummaryCalculator.calculate(listOf(alice, bob, carol), invoices, now)

        assertMoney("210.00", result.totalCabinetBalance)
        assertEquals(3, result.unpaidInvoiceCount)
        assertMoney("220.00", result.unpaidInvoiceAmount)
        assertMoney("140.00", result.balanceAppliedToDebt)
        assertMoney("80.00", result.netOutstandingDebt)
        assertEquals(1, result.debtorCount)
        assertMoney("80.00", result.averageDebt)
        assertMoney("70.00", result.unappliedCredit)
        assertEquals("Alice", result.debtors.single().subscriberName)
        assertMoney("100.00", result.debtors.single().debtSharePercent)

        val oldestResult = assertNotNull(result.oldestUnpaidInvoice)
        assertEquals(oldest.id, oldestResult.invoiceId)
        assertEquals(30, oldestResult.ageDays)
    }

    @Test
    fun `returns zero averages and shares when balances cover all invoices`() {
        val now = Instant.parse("2026-07-01T12:00:00Z")
        val subscriber = subscriber("Covered", "100.00")
        val result = FinanceSummaryCalculator.calculate(
            listOf(subscriber),
            listOf(invoice(subscriber, "25.00", "2026-07-01T12:00:00Z")),
            now
        )

        assertMoney("0", result.netOutstandingDebt)
        assertEquals(0, result.debtorCount)
        assertMoney("0", result.averageDebt)
        assertEquals(emptyList(), result.debtors)
        assertMoney("75.00", result.unappliedCredit)
    }

    private fun subscriber(name: String, balance: String) =
        Subscriber().apply {
            id = UUID.randomUUID()
            this.name = name
            email = "${name.lowercase()}@example.com"
            this.balance = BigDecimal(balance)
        }

    private fun invoice(subscriber: Subscriber, amount: String, createdAt: String) =
        Invoice().apply {
            id = UUID.randomUUID()
            this.subscriber = subscriber
            totalAmount = BigDecimal(amount)
            status = InvoiceStatus.SENT
            this.createdAt = Instant.parse(createdAt)
        }

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }
}
