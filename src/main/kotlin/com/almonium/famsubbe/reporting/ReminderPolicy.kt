package com.almonium.famsubbe.reporting

import com.almonium.famsubbe.subscriber.UnpaidInvoiceDto
import com.almonium.famsubbe.util.BillingTime
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Who gets the Saturday check-up. Every invoice already gets its own email
 * when it is created, so the check-up is only a reminder, and a weekly
 * reminder about ₴31.93 is noise:
 *
 * - nothing owed: no reminder;
 * - an invoice created in the last week: no reminder, its email was enough;
 * - less than [smallDebtThreshold]: only on the first Saturday of the month;
 * - otherwise: every Saturday.
 */
class ReminderPolicy(private val smallDebtThreshold: BigDecimal) {

    /** Why no reminder goes out today, or null when one should. */
    fun skipReason(owed: BigDecimal, unpaidInvoices: List<UnpaidInvoiceDto>, today: LocalDate): String? {
        if (owed <= BigDecimal.ZERO) {
            return "Skipped because subscriber does not currently owe anything"
        }
        val newestInvoiceDate = unpaidInvoices.maxOfOrNull { BillingTime.dateOf(it.createdAt) }
        if (newestInvoiceDate != null && newestInvoiceDate > today.minusDays(GRACE_DAYS)) {
            return "Skipped because an invoice was emailed in the last $GRACE_DAYS days"
        }
        if (owed < smallDebtThreshold && today.dayOfMonth > 7) {
            return "Skipped because ₴$owed is under ₴$smallDebtThreshold, reminded only on the first Saturday of the month"
        }
        return null
    }

    companion object {
        const val GRACE_DAYS = 7L
    }
}
