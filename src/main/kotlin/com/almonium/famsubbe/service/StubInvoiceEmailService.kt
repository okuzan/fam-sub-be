package com.almonium.famsubbe.service

import com.almonium.famsubbe.entity.Invoice
import com.almonium.famsubbe.entity.LedgerEntry

class StubInvoiceEmailService : InvoiceEmailService {
    override fun sendInvoiceEmail(invoice: Invoice, entries: List<LedgerEntry>): Boolean {
        return true
    }
}