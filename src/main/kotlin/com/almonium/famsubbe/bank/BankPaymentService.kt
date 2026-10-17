package com.almonium.famsubbe.bank

import com.almonium.famsubbe.invoice.IncomingPaymentResult
import com.almonium.famsubbe.invoice.Invoice
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.invoice.InvoiceService
import com.almonium.famsubbe.invoice.InvoiceStatus
import com.almonium.famsubbe.subscriber.PaymentCode
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import com.almonium.famsubbe.subscriber.SubscriberStandingService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Turns incoming bank transfers into subscriber payments. A transfer applies on
 * its own when its purpose carries a subscriber's payment code, when the
 * sender's name holds exactly one subscriber's name and surname, or, failing
 * both, when the sender is remembered; anything else waits for an admin to
 * assign or ignore it. Applying a payment credits the subscriber's balance and
 * settles their invoices from it (see [InvoiceService.applyIncomingPayment]).
 */
@Service
class BankPaymentService(
    private val transactionRepository: BankTransactionRepository,
    private val senderRepository: BankSenderRepository,
    private val subscriberRepository: SubscriberRepository,
    private val invoiceRepository: InvoiceRepository,
    private val invoiceService: InvoiceService,
    private val standingService: SubscriberStandingService,
    private val monoProperties: MonoProperties
) {
    private val log = LoggerFactory.getLogger(BankPaymentService::class.java)

    /**
     * Stores one statement item and applies it when the sender is known. Runs
     * in its own transaction: the unique key on the bank's operation id makes a
     * second import of the same item fail and roll back, so it never applies twice.
     */
    @Transactional
    fun importMonoItem(item: MonoStatementItem): BankImportOutcome {
        if (item.amount <= 0) return BankImportOutcome.NOT_A_PAYMENT
        // A coded transfer counts even when the bank names no sender.
        val code = PaymentCode.findIn(item.comment, item.description)
        val sender = BankSenderIdentity.of(item)
        if (sender == null && code == null) return BankImportOutcome.NOT_A_PAYMENT
        if (transactionRepository.existsByProviderAndExternalId(BankProvider.MONO, item.id)) {
            return BankImportOutcome.DUPLICATE
        }

        val now = Instant.now()
        val transaction = BankTransaction().apply {
            provider = BankProvider.MONO
            externalId = item.id
            accountId = monoProperties.accountId
            occurredAt = Instant.ofEpochSecond(item.time)
            amount = BigDecimal.valueOf(item.amount, 2)
            description = item.description
            comment = item.comment
            counterName = item.counterName
            counterIban = item.counterIban
            senderKey = sender?.key
            senderName = sender?.name
            createdAt = now
        }

        val codedSubscriber = code?.let { subscriberRepository.findByPaymentCode(it) }
        val namedSubscriber = sender?.let { subscriberNamed(it.name) }
        val rule = sender?.let { senderRepository.findBySenderKey(it.key) }
        val ruleSubscriber = rule?.subscriber
        when {
            // The purpose carries the code the pay page prefilled: whose money it
            // is is beyond doubt, whatever the amount and whoever sent it.
            codedSubscriber != null -> {
                applyTo(transaction, codedSubscriber, BankMatchMethod.PAYMENT_CODE, null)
                if (rule == null) remember(transaction, codedSubscriber, null)
            }
            namedSubscriber != null -> applyIfOwed(transaction, namedSubscriber, BankMatchMethod.NAME)
            // Remembered senders are the fallback, for someone paying under a
            // name that is not the subscriber's own.
            rule?.ignored == true -> {
                transaction.status = BankTransactionStatus.IGNORED
                transaction.reviewNote = "Sender is on the ignore list"
                transaction.resolvedAt = now
            }
            ruleSubscriber != null -> applyIfOwed(transaction, ruleSubscriber, BankMatchMethod.SENDER_RULE)
        }

        transactionRepository.saveAndFlush(transaction)
        return when (transaction.status) {
            BankTransactionStatus.APPLIED -> BankImportOutcome.APPLIED
            BankTransactionStatus.IGNORED -> BankImportOutcome.IGNORED
            BankTransactionStatus.UNMATCHED -> BankImportOutcome.NEEDS_REVIEW
        }
    }

    @Transactional(readOnly = true)
    fun getTransactions(status: BankTransactionStatus?, limit: Int): List<BankTransactionResponse> {
        val page = PageRequest.of(0, limit.coerceIn(1, MAX_LIMIT))
        val transactions = if (status == null) {
            transactionRepository.findAllByOrderByOccurredAtDesc(page)
        } else {
            transactionRepository.findByStatusOrderByOccurredAtDesc(status, page)
        }
        val suggestions = suggestionsFor(transactions.filter { it.status == BankTransactionStatus.UNMATCHED })
        return transactions.map { it.toResponse(suggestions[it.id].orEmpty()) }
    }

    @Transactional(readOnly = true)
    fun getTransaction(transactionId: UUID): BankTransactionResponse =
        findTransaction(transactionId).toResponse(emptyList())

    @Transactional(readOnly = true)
    fun getSubscriberPayments(subscriberId: UUID, limit: Int): List<SubscriberPaymentResponse> {
        val payments = transactionRepository.findBySubscriberIdAndStatusOrderByOccurredAtDesc(
            subscriberId,
            BankTransactionStatus.APPLIED,
            PageRequest.of(0, limit.coerceIn(1, MAX_LIMIT))
        )
        val settledByPayment = invoiceRepository
            .findByPaidByBankTransactionIdIn(payments.mapNotNull { it.id })
            .groupBy { it.paidByBankTransactionId }

        return payments.map { payment ->
            SubscriberPaymentResponse(
                id = requireNotNull(payment.id),
                occurredAt = requireNotNull(payment.occurredAt),
                amount = requireNotNull(payment.amount),
                senderName = payment.senderName,
                settledPeriods = settledByPayment[payment.id].orEmpty()
                    .sortedBy { it.fromMonth }
                    .map { invoice ->
                        if (invoice.fromMonth == invoice.toMonth) "${invoice.fromMonth}"
                        else "${invoice.fromMonth}..${invoice.toMonth}"
                    }
            )
        }
    }

    @Transactional
    fun assign(transactionId: UUID, request: BankPaymentAssignRequest, accountId: UUID): BankPaymentResolution {
        val transaction = findTransaction(transactionId)
        check(transaction.status != BankTransactionStatus.APPLIED) {
            "This payment was already applied to ${transaction.subscriber?.name}"
        }
        val subscriber = subscriberRepository.findById(request.subscriberId)
            .orElseThrow { NoSuchElementException("Subscriber not found: ${request.subscriberId}") }

        val result = applyTo(transaction, subscriber, BankMatchMethod.ADMIN, accountId)
        val remembered = request.rememberSender && remember(transaction, subscriber, accountId)

        return BankPaymentResolution(
            transaction = transactionRepository.save(transaction).toResponse(emptyList()),
            settledInvoiceIds = result.settledInvoiceIds,
            balanceAfter = result.balanceAfter,
            senderRemembered = remembered
        )
    }

    @Transactional
    fun ignore(transactionId: UUID, request: BankPaymentIgnoreRequest, accountId: UUID): BankPaymentResolution {
        val transaction = findTransaction(transactionId)
        check(transaction.status == BankTransactionStatus.UNMATCHED) {
            "Only unmatched payments can be ignored"
        }

        transaction.status = BankTransactionStatus.IGNORED
        transaction.reviewNote = null
        transaction.resolvedAt = Instant.now()
        transaction.resolvedByAccountId = accountId

        val remembered = request.ignoreSender && remember(transaction, null, accountId)

        return BankPaymentResolution(
            transaction = transactionRepository.save(transaction).toResponse(emptyList()),
            settledInvoiceIds = emptyList(),
            balanceAfter = null,
            senderRemembered = remembered
        )
    }

    /**
     * Takes back a payment applied by mistake: reopens the invoices it paid,
     * which returns what they drew from the balance, then takes the transfer
     * off the balance, and puts the transfer back in review. Refused when part
     * of the credit has since paid something else, since that would leave the
     * balance negative.
     */
    @Transactional
    fun undo(transactionId: UUID): BankPaymentUndoResult {
        val transaction = findTransaction(transactionId)
        check(transaction.status == BankTransactionStatus.APPLIED) { "Only applied payments can be undone" }
        val subscriber = requireNotNull(transaction.subscriber)
        val amount = requireNotNull(transaction.amount)

        val reopened = invoiceRepository.findByPaidByBankTransactionIdIn(listOf(transactionId))
            .filter { it.status == InvoiceStatus.PAID }
            .map { invoice ->
                val reopenTo = if (invoice.sentAt != null) InvoiceStatus.SENT else InvoiceStatus.DRAFT
                invoiceService.updateStatus(requireNotNull(invoice.id), reopenTo).invoice.id
            }

        val balanceAfter = (subscriber.balance ?: BigDecimal.ZERO).subtract(amount)
        check(balanceAfter.signum() >= 0) {
            "${subscriber.name}'s balance is ₴${subscriber.balance}, less than this ₴$amount payment: " +
                "part of it has already paid other invoices. Reopen those first."
        }
        subscriber.balance = balanceAfter
        subscriberRepository.save(subscriber)

        transaction.status = BankTransactionStatus.UNMATCHED
        transaction.subscriber = null
        transaction.matchMethod = null
        transaction.settledInvoiceCount = 0
        transaction.settledAmount = null
        transaction.resolvedAt = null
        transaction.resolvedByAccountId = null
        transaction.reviewNote = "Was applied to ${subscriber.name}, then undone."

        val saved = transactionRepository.save(transaction)
        return BankPaymentUndoResult(
            transaction = saved.toResponse(suggestionsFor(listOf(saved))[saved.id].orEmpty()),
            subscriberId = requireNotNull(subscriber.id),
            subscriberName = subscriber.name.orEmpty(),
            reopenedInvoiceIds = reopened,
            balanceAfter = balanceAfter
        )
    }

    @Transactional(readOnly = true)
    fun getSenders(): List<BankSenderResponse> =
        senderRepository.findAllWithSubscriber().map { it.toResponse() }

    @Transactional
    fun forgetSender(senderId: UUID): BankSenderResponse {
        val sender = senderRepository.findById(senderId)
            .orElseThrow { NoSuchElementException("Sender not found: $senderId") }
        val response = sender.toResponse()
        senderRepository.delete(sender)
        return response
    }

    /**
     * Applies a payment recognised by who sent it, unless it is more than they
     * owe: friends also send each other money that has nothing to do with FamSub.
     */
    private fun applyIfOwed(transaction: BankTransaction, subscriber: Subscriber, method: BankMatchMethod) {
        val amount = requireNotNull(transaction.amount)
        val owed = standingService.amountOwed(subscriber)
        if (amount <= owed + monoProperties.autoApplyTolerance) {
            applyTo(transaction, subscriber, method, null)
        } else {
            transaction.reviewNote = "₴$amount is more than ${subscriber.name} owes " +
                "(₴${owed.max(BigDecimal.ZERO)}). Check it is FamSub money before applying."
        }
    }

    /**
     * The one subscriber whose name and surname both appear in [senderName], in
     * any order and case, or null when none or several do. Subscribers without a
     * surname never match: a first name alone is not enough to tell people apart.
     */
    private fun subscriberNamed(senderName: String): Subscriber? {
        val senderWords = senderName.words()
        return subscriberRepository.findBySurnameIsNotNull()
            .filter { senderWords.containsAll(it.fullNameWords()) }
            .singleOrNull()
    }

    private fun Subscriber.fullNameWords(): Set<String> = "$name $surname".words()

    private fun applyTo(
        transaction: BankTransaction,
        subscriber: Subscriber,
        method: BankMatchMethod,
        accountId: UUID?
    ): IncomingPaymentResult {
        // Invoices point at the transfer that paid them, so it needs its id first.
        if (transaction.id == null) transactionRepository.saveAndFlush(transaction)
        val result = invoiceService.applyIncomingPayment(
            subscriber,
            requireNotNull(transaction.amount),
            transaction.id
        )

        transaction.status = BankTransactionStatus.APPLIED
        transaction.subscriber = subscriber
        transaction.matchMethod = method
        transaction.reviewNote = null
        transaction.settledInvoiceCount = result.settledInvoiceIds.size
        transaction.settledAmount = result.settledAmount
        transaction.resolvedAt = Instant.now()
        transaction.resolvedByAccountId = accountId

        log.info(
            "Applied bank payment {} of {} to subscriber {} ({}): settled invoices {}, balance now {}",
            transaction.externalId,
            transaction.amount,
            subscriber.id,
            method,
            result.settledInvoiceIds,
            result.balanceAfter
        )
        return result
    }

    /** Points the transaction's sender at a subscriber, or at the ignore list when [subscriber] is null. */
    private fun remember(transaction: BankTransaction, subscriber: Subscriber?, accountId: UUID?): Boolean {
        val key = transaction.senderKey ?: return false
        val sender = senderRepository.findBySenderKey(key) ?: BankSender().apply {
            senderKey = key
            createdAt = Instant.now()
            createdByAccountId = accountId
        }
        sender.senderName = transaction.senderName
        sender.subscriber = subscriber
        sender.ignored = subscriber == null
        senderRepository.save(sender)
        return true
    }

    private fun findTransaction(id: UUID): BankTransaction =
        transactionRepository.findById(id)
            .orElseThrow { NoSuchElementException("Bank payment not found: $id") }

    /**
     * Likely owners of each unmatched payment: a remembered sender first, then
     * subscribers whose debt or an unpaid invoice equals the amount, or whose
     * name matches the sender's.
     */
    private fun suggestionsFor(unmatched: List<BankTransaction>): Map<UUID, List<BankPaymentSuggestion>> {
        if (unmatched.isEmpty()) return emptyMap()

        val subscribers = subscriberRepository.findAllByOrderByName()
        val unpaidBySubscriber = invoiceRepository
            .findByStatusInWithSubscriber(SubscriberStandingService.UNPAID_STATUSES)
            .groupBy { requireNotNull(it.subscriber?.id) }
        val sendersByKey = senderRepository.findAllWithSubscriber().associateBy { it.senderKey }

        return unmatched.associate { transaction ->
            val amount = requireNotNull(transaction.amount)
            val senderWords = transaction.senderName.words()
            val remembered = transaction.senderKey?.let { sendersByKey[it]?.subscriber?.id }

            val ranked = subscribers.mapNotNull { subscriber ->
                val subscriberId = requireNotNull(subscriber.id)
                val invoices: List<Invoice> = unpaidBySubscriber[subscriberId].orEmpty()
                val reasons = buildList {
                    if (subscriberId == remembered) add("remembered sender")
                    if (invoices.any { it.totalAmount?.compareTo(amount) == 0 }) {
                        add("has an unpaid invoice of ₴$amount")
                    } else if (SubscriberStandingService.amountOwed(invoices, subscriber).compareTo(amount) == 0) {
                        add("owes exactly ₴$amount")
                    }
                    if (subscriber.surname != null && senderWords.containsAll(subscriber.fullNameWords())) {
                        add(FULL_NAME_REASON)
                    } else if (subscriber.name.words().any { it in senderWords }) {
                        add("name matches the sender")
                    }
                }
                reasons.takeIf { it.isNotEmpty() }?.let { Triple(subscriber, subscriberId == remembered, it) }
            }
                // A remembered sender, then a full-name match, outrank amounts: many people owe the same ₴31.93.
                .sortedWith(compareByDescending<Triple<Subscriber, Boolean, List<String>>> { it.second }
                    .thenByDescending { FULL_NAME_REASON in it.third }
                    .thenByDescending { it.third.size })
                .take(MAX_SUGGESTIONS)
                .map { (subscriber, _, reasons) ->
                    BankPaymentSuggestion(
                        subscriberId = requireNotNull(subscriber.id),
                        subscriberName = subscriber.name.orEmpty(),
                        reason = reasons.joinToString(", ").replaceFirstChar { it.uppercase() }
                    )
                }

            requireNotNull(transaction.id) to ranked
        }
    }

    /** Lowercased words, with the apostrophe variants of Ukrainian names made one. */
    private fun String?.words(): Set<String> =
        orEmpty().lowercase()
            .replace(APOSTROPHES, "'")
            .split(' ', '-')
            .filter { it.length > 1 }
            .toSet()

    private fun BankTransaction.toResponse(suggestions: List<BankPaymentSuggestion>) = BankTransactionResponse(
        id = requireNotNull(id),
        occurredAt = requireNotNull(occurredAt),
        amount = requireNotNull(amount),
        senderName = senderName,
        description = description,
        comment = comment,
        counterIban = counterIban,
        status = status.name,
        subscriberId = subscriber?.id,
        subscriberName = subscriber?.name,
        matchMethod = matchMethod?.name,
        reviewNote = reviewNote,
        settledInvoiceCount = settledInvoiceCount,
        settledAmount = settledAmount,
        resolvedAt = resolvedAt,
        suggestions = suggestions
    )

    private fun BankSender.toResponse() = BankSenderResponse(
        id = requireNotNull(id),
        senderName = senderName,
        subscriberId = subscriber?.id,
        subscriberName = subscriber?.name,
        ignored = ignored,
        learnedAutomatically = createdByAccountId == null,
        createdAt = requireNotNull(createdAt)
    )

    private companion object {
        const val MAX_LIMIT = 500
        const val MAX_SUGGESTIONS = 5
        const val FULL_NAME_REASON = "name and surname match the sender"
        val APOSTROPHES = Regex("[’ʼ`´‘]")
    }
}
