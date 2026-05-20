package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.ActiveSubscriptionDto
import com.almonium.famsubbe.dto.WeeklySituationInvoiceDto
import com.almonium.famsubbe.entity.Invoice
import com.almonium.famsubbe.entity.LedgerEntry
import java.math.BigDecimal

interface InvoiceEmailService {
    fun sendInvoiceEmail(invoice: Invoice, entries: List<LedgerEntry>): Boolean
    fun sendSituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        unpaidInvoicesCount: Int,
        activeSubscriptionsCount: Int,
        activeSubscriptionNames: List<String> = emptyList()
    ): Boolean

    fun sendWeeklySituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: BigDecimal,
        subscriberBalance: BigDecimal,
        activeSubscriptions: List<ActiveSubscriptionDto>,
        unpaidInvoices: List<WeeklySituationInvoiceDto>
    ): Boolean
}
