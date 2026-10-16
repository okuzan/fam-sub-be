package com.almonium.famsubbe.bank

import com.almonium.famsubbe.invoice.IncomingPaymentResult
import com.almonium.famsubbe.invoice.Invoice
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.invoice.InvoiceService
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
 * Turns incoming bank transfers into subscriber payments. A transfer from a
 * remembered sender applies on its own; anything else waits for an admin to
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
        val sender = BankSenderIdentity.of(item) ?: return BankImportOutcome.NOT_A_PAYMENT
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
            senderKey = sender.key
            senderName = sender.name
            createdAt = now
        }

        val rule = senderRepository.findBySenderKey(sender.key)
        val ruleSubscriber = rule?.subscriber
        when {
            rule?.ignored == true -> {
                transaction.status = BankTransactionStatus.IGNORED
                transaction.reviewNote = "Sender is on the ignore list"
                transaction.resolvedAt = now
            }
            ruleSubscriber != null -> {
                val amount = requireNotNull(transaction.amount)
                val owed = standingService.amountOwed(ruleSubscriber)
                if (amount <= owed + monoProperties.autoApplyTolerance) {
                    applyTo(transaction, ruleSubscriber, BankMatchMethod.SENDER_RULE, null)
                } else {
                    transaction.reviewNote = "₴$amount is more than ${ruleSubscriber.name} owes " +
                        "(₴${owed.max(BigDecimal.ZERO)}). Check it is FamSub money before applying."
                }
            }
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

    private fun applyTo(
        transaction: BankTransaction,
        subscriber: Subscriber,
        method: BankMatchMethod,
        accountId: UUID?
    ): IncomingPaymentResult {
        val result = invoiceService.applyIncomingPayment(subscriber, requireNotNull(transaction.amount))

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
                    if (subscriber.name.words().any { it in senderWords }) add("name matches the sender")
                }
                reasons.takeIf { it.isNotEmpty() }?.let { Triple(subscriber, subscriberId == remembered, it) }
            }
                .sortedWith(compareByDescending<Triple<Subscriber, Boolean, List<String>>> { it.second }
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

    private fun String?.words(): Set<String> =
        orEmpty().lowercase().split(' ', '-').filter { it.length > 1 }.toSet()

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
    }
}
