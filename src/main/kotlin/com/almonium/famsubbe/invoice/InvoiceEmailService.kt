package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.subscriber.ActiveSubscriptionDto
import com.almonium.famsubbe.dto.WeeklySituationInvoiceDto
import com.almonium.famsubbe.accounting.LedgerEntry
import java.math.BigDecimal

interface InvoiceEmailService {
    fun sendInvoiceEmail(invoice: Invoice, entries: List<LedgerEntry>, totalAmountOwed: BigDecimal): Boolean
    fun sendSituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>
    ): Boolean

    fun sendWeeklySituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>
    ): Boolean

    fun sendDebtPaidEmail(
        toEmail: String,
        subscriberName: String,
        paidInvoicesCount: Int,
        totalPaidAmount: BigDecimal,
        balanceAfter: BigDecimal,
        creditWrittenOff: BigDecimal
    ): Boolean
}
