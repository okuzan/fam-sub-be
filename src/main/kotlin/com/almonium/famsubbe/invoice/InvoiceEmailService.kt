package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.subscriber.ActiveSubscriptionDto
import com.almonium.famsubbe.reporting.WeeklySituationInvoiceDto
import com.almonium.famsubbe.accounting.LedgerEntry
import com.almonium.famsubbe.payment.PaymentDetails
import java.math.BigDecimal

interface InvoiceEmailService {
    fun sendInvoiceEmail(invoice: Invoice, entries: List<LedgerEntry>, totalAmountOwed: BigDecimal): Boolean
    fun sendSituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>,
        payment: PaymentDetails
    ): Boolean

    fun sendWeeklySituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>,
        payment: PaymentDetails
    ): Boolean

    fun sendDebtPaidEmail(
        toEmail: String,
        subscriberName: String,
        paidInvoicesCount: Int,
        totalPaidAmount: BigDecimal,
        balanceAfter: BigDecimal,
        creditWrittenOff: BigDecimal
    ): Boolean

    fun sendPaymentReceivedEmail(email: PaymentReceivedEmail): Boolean

    /** [totalAmountOwed] is what the subscriber owes now that [invoice] is void. */
    fun sendInvoiceCancelledEmail(invoice: Invoice, totalAmountOwed: BigDecimal): Boolean
}
