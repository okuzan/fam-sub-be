package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.accounting.LedgerEntry
import com.almonium.famsubbe.payment.PaymentDetails
import com.almonium.famsubbe.reporting.WeeklySituationInvoiceDto
import com.almonium.famsubbe.subscriber.ActiveSubscriptionDto
import java.math.BigDecimal

/** Mockito's any() hands Kotlin's non-null parameters a null, so tests use this instead of a mock. */
internal class FakeInvoiceEmailService : InvoiceEmailService {
    var succeeds = true

    override fun sendInvoiceEmail(invoice: Invoice, entries: List<LedgerEntry>, totalAmountOwed: BigDecimal) = succeeds

    override fun sendSituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>,
        payment: PaymentDetails
    ) = succeeds

    override fun sendWeeklySituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>,
        payment: PaymentDetails
    ) = succeeds

    override fun sendDebtPaidEmail(
        toEmail: String,
        subscriberName: String,
        paidInvoicesCount: Int,
        totalPaidAmount: BigDecimal,
        balanceAfter: BigDecimal,
        creditWrittenOff: BigDecimal
    ) = succeeds
}
