package com.almonium.famsubbe.invoice

data class InvoiceDetailResponse(
    val invoice: InvoiceResponse,
    val entries: List<InvoiceLedgerEntryResponse>
)