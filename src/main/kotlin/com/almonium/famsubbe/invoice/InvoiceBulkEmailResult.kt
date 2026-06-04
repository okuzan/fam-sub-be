package com.almonium.famsubbe.invoice

import java.util.*

data class InvoiceBulkEmailResult(
    val attemptedCount: Int,
    val sentCount: Int,
    val updatedCount: Int,
    val failedCount: Int,
    val dryRun: Boolean,
    val items: List<InvoiceBulkEmailItemResult>
)

data class InvoiceBulkEmailItemResult(
    val invoiceId: UUID,
    val subscriberId: UUID,
    val subscriberName: String,
    val statusBefore: String,
    val statusAfter: String,
    val sent: Boolean,
    val updated: Boolean,
    val message: String
)
