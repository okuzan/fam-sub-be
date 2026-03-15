package com.almonium.famsubbe.dto

data class InvoiceDetailResponse(
    val invoice: InvoiceResponse,
    val entries: List<InvoiceLedgerEntryResponse>
)