package com.almonium.famsubbe.service

import com.almonium.famsubbe.entity.Invoice
import com.almonium.famsubbe.entity.LedgerEntry

interface InvoiceEmailService {
    fun sendInvoiceEmail(invoice: Invoice, entries: List<LedgerEntry>): Boolean
    fun sendSituationEmail(
        toEmail: String,
        subscriberName: String,
        totalOwed: java.math.BigDecimal,
        unpaidInvoicesCount: Int,
        activeSubscriptionsCount: Int,
        activeSubscriptionNames: List<String> = emptyList()
    ): Boolean
}
