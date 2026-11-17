package com.almonium.famsubbe.credit

import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.invoice.InvoiceService
import com.almonium.famsubbe.invoice.InvoiceStatus
import com.almonium.famsubbe.subscriber.SubscriberRepository
import com.almonium.famsubbe.util.BillingTime
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.*

/** A credit note was recorded and should be emailed once it commits. */
data class CreditNoteCreatedEvent(val creditNoteId: UUID)

/**
 * Money a subscriber spent on the group's behalf: paid for the admin's dinner,
 * bought a shared subscription with their own card. Unlike typing a new
 * balance, it keeps who, how much, when and why, settles open invoices the way
 * a bank transfer does, and can be undone.
 */
@Service
@Transactional
class CreditNoteService(
    private val creditNoteRepository: CreditNoteRepository,
    private val subscriberRepository: SubscriberRepository,
    private val invoiceRepository: InvoiceRepository,
    private val invoiceService: InvoiceService,
    private val events: ApplicationEventPublisher
) {

    @Transactional(readOnly = true)
    fun getCreditNotes(subscriberId: UUID?): List<CreditNoteResponse> {
        val notes = if (subscriberId != null) {
            creditNoteRepository.findBySubscriberIdWithSubscriber(subscriberId)
        } else {
            creditNoteRepository.findAllWithSubscriber()
        }
        return notes.map { it.toResponse() }
    }

    fun create(request: CreditNoteCreateRequest, performedByAccountId: UUID): CreditNoteCreateResult {
        val subscriberId = requireNotNull(request.subscriberId) { "subscriberId is required" }
        val subscriber = subscriberRepository.findById(subscriberId)
            .orElseThrow { NoSuchElementException("Subscriber not found: $subscriberId") }
        // Family covered by the waiver never has a balance to credit; see Subscriber.billingExempt.
        check(!subscriber.billingExempt) {
            "${subscriber.name} is billing exempt, so FamSub keeps no balance for them"
        }
        val amount = requireNotNull(request.amount) { "amount is required" }.setScale(2, RoundingMode.UNNECESSARY)
        val reason = request.reason?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("reason is required")
        val occurredOn = request.occurredOn ?: BillingTime.today()
        require(!occurredOn.isAfter(BillingTime.today())) { "occurredOn cannot be in the future" }

        val note = creditNoteRepository.save(
            CreditNote().apply {
                this.subscriber = subscriber
                this.amount = amount
                this.occurredOn = occurredOn
                this.reason = reason
                createdAt = Instant.now()
                createdByAccountId = performedByAccountId
            }
        )
        val noteId = requireNotNull(note.id)

        val applied = invoiceService.applyIncomingPayment(subscriber, amount, creditNoteId = noteId)
        note.settledInvoiceCount = applied.settledInvoiceIds.size
        note.settledAmount = applied.settledAmount
        val saved = creditNoteRepository.save(note)

        if (request.sendEmail) events.publishEvent(CreditNoteCreatedEvent(noteId))
        return CreditNoteCreateResult(saved.toResponse(), applied.settledInvoiceIds, applied.balanceAfter)
    }

    /**
     * Takes a mistaken credit note back, as BankPaymentService.undo does for a
     * transfer: reopens the invoices it paid, then debits its amount. Refuses
     * when the credit has since paid something else.
     */
    fun undo(creditNoteId: UUID, performedByAccountId: UUID): CreditNoteUndoResult {
        val note = creditNoteRepository.findById(creditNoteId)
            .orElseThrow { NoSuchElementException("Credit note not found: $creditNoteId") }
        check(note.undoneAt == null) { "This credit note is already undone" }
        val subscriber = requireNotNull(note.subscriber)
        val amount = requireNotNull(note.amount)

        val reopened = invoiceRepository.findByPaidByCreditNoteId(creditNoteId)
            .filter { it.status == InvoiceStatus.PAID }
            .map { invoice ->
                val reopenTo = if (invoice.sentAt != null) InvoiceStatus.SENT else InvoiceStatus.DRAFT
                requireNotNull(invoiceService.updateStatus(requireNotNull(invoice.id), reopenTo).invoice.id)
            }

        val balanceAfter = (subscriber.balance ?: BigDecimal.ZERO).subtract(amount)
        check(balanceAfter.signum() >= 0) {
            "${subscriber.name}'s balance is ₴${subscriber.balance}, less than this ₴$amount credit note: " +
                "part of it has already paid other invoices. Reopen those first."
        }
        subscriber.balance = balanceAfter
        subscriberRepository.save(subscriber)

        note.undoneAt = Instant.now()
        note.undoneByAccountId = performedByAccountId
        val saved = creditNoteRepository.save(note)
        return CreditNoteUndoResult(saved.toResponse(), reopened, balanceAfter)
    }

    private fun CreditNote.toResponse(): CreditNoteResponse =
        CreditNoteResponse(
            id = requireNotNull(id),
            subscriberId = requireNotNull(subscriber?.id),
            subscriberName = subscriber?.name.orEmpty(),
            amount = requireNotNull(amount),
            occurredOn = requireNotNull(occurredOn),
            reason = reason.orEmpty(),
            createdAt = requireNotNull(createdAt),
            createdByAccountId = requireNotNull(createdByAccountId),
            settledInvoiceCount = settledInvoiceCount,
            settledAmount = settledAmount,
            emailSentAt = emailSentAt,
            undoneAt = undoneAt,
            undoneByAccountId = undoneByAccountId
        )
}
