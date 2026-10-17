package com.almonium.famsubbe.bank

import com.almonium.famsubbe.accounting.Charge
import com.almonium.famsubbe.accounting.ChargeRepository
import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.subscription.SubscriptionService
import com.almonium.famsubbe.subscription.SubscriptionServiceRepository
import com.almonium.famsubbe.util.BillingTime
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.util.*
import kotlin.math.absoluteValue

/**
 * Turns subscription bills paid from the card into charges. A card payment
 * whose merchant name, currency and original amount match a
 * [BankMerchantRule] is added to that service's charge for its month, creating
 * the charge when there is none. A payment from a known merchant that matches
 * no amount waits for an admin, and so does one whose charge is already split
 * into ledger entries or was entered by hand: those are never changed on their
 * own. Payments to merchants no rule mentions are not stored at all.
 */
@Service
class BankChargeService(
    private val debitRepository: BankDebitRepository,
    private val ruleRepository: BankMerchantRuleRepository,
    private val chargeRepository: ChargeRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val subscriptionServiceRepository: SubscriptionServiceRepository,
    private val monoProperties: MonoProperties
) {
    private val log = LoggerFactory.getLogger(BankChargeService::class.java)

    /**
     * Stores one outgoing statement item when it is a payment to a known
     * merchant, and charges it when a rule says which bill it is. Runs in its
     * own transaction: the unique key on the bank's operation id makes a second
     * import of the same item fail and roll back, so it is never charged twice.
     */
    @Transactional
    fun importMonoDebit(item: MonoStatementItem, now: Instant = Instant.now()): BankDebitOutcome {
        if (item.amount >= 0) return BankDebitOutcome.NOT_A_SUBSCRIPTION
        val rules = ruleRepository.findAllWithService()
        val merchant = merchantOf(item.description, rules) ?: return BankDebitOutcome.NOT_A_SUBSCRIPTION
        if (debitRepository.existsByProviderAndExternalId(BankProvider.MONO, item.id)) {
            return BankDebitOutcome.DUPLICATE
        }
        // The converted amount can still change until the payment settles. A
        // hold the bank never settles is taken as it is once the sync stops
        // re-reading that far back.
        val occurredAt = Instant.ofEpochSecond(item.time)
        if (item.hold && occurredAt > now.minus(HOLD_PATIENCE)) return BankDebitOutcome.PENDING

        val debit = BankDebit().apply {
            provider = BankProvider.MONO
            externalId = item.id
            accountId = monoProperties.accountId
            this.occurredAt = occurredAt
            amount = BigDecimal.valueOf(item.amount.absoluteValue, 2)
            originalAmount = BigDecimal.valueOf(item.operationAmount.absoluteValue, fractionDigits(item.currencyCode))
                .setScale(2, RoundingMode.HALF_UP)
            currencyCode = item.currencyCode
            description = item.description
            mcc = item.mcc
            this.merchant = merchant
            createdAt = now
        }

        val rule = rules.firstOrNull {
            it.merchant == merchant &&
                it.currencyCode == debit.currencyCode &&
                it.amount?.compareTo(debit.originalAmount) == 0
        }
        when {
            rule == null -> debit.reviewNote =
                "No $merchant bill of ${debit.originalText()} is known. Charge it to a service, or ignore it."
            rule.ignored -> {
                debit.status = BankDebitStatus.IGNORED
                debit.reviewNote = "Matches an ignore rule"
                debit.resolvedAt = now
            }
            else -> {
                val service = requireNotNull(rule.subscriptionService)
                val month = chargeMonthOf(occurredAt)
                val blocker = blockerFor(service, month, automatic = true)
                if (blocker == null) {
                    addToCharge(debit, service, month, BankChargeMatchMethod.RULE, null)
                } else {
                    debit.subscriptionService = service
                    debit.reviewNote = blocker
                }
            }
        }

        debitRepository.saveAndFlush(debit)
        return when (debit.status) {
            BankDebitStatus.CHARGED -> BankDebitOutcome.CHARGED
            BankDebitStatus.IGNORED -> BankDebitOutcome.IGNORED
            BankDebitStatus.UNMATCHED -> BankDebitOutcome.NEEDS_REVIEW
        }
    }

    @Transactional(readOnly = true)
    fun getOverview(status: BankDebitStatus?, limit: Int): BankChargesOverview {
        val page = PageRequest.of(0, limit.coerceIn(1, MAX_LIMIT))
        val debits = if (status == null) {
            debitRepository.findLatest(page)
        } else {
            debitRepository.findLatestByStatus(status, page)
        }
        val rules = ruleRepository.findAllWithService()
        return BankChargesOverview(
            debits = debits.map { it.toResponse(rules) },
            rules = rules.map { it.toResponse() }
        )
    }

    /** Charges a payment an admin recognised, to the month they chose or the one it belongs to by date. */
    @Transactional
    fun charge(debitId: UUID, request: BankDebitChargeRequest, accountId: UUID): BankDebitResponse {
        val debit = findDebit(debitId)
        check(debit.status != BankDebitStatus.CHARGED) {
            "This payment is already part of the ${debit.chargeMonth} ${debit.subscriptionService?.name} charge"
        }
        val service = subscriptionServiceRepository.findById(request.subscriptionServiceId)
            .orElseThrow { NoSuchElementException("Subscription service not found: ${request.subscriptionServiceId}") }
        val month = request.chargeMonth ?: chargeMonthOf(requireNotNull(debit.occurredAt))

        blockerFor(service, month, automatic = false)?.let { throw IllegalStateException(it) }
        addToCharge(debit, service, month, BankChargeMatchMethod.ADMIN, accountId)
        if (request.rememberAmount) remember(debit, service, accountId)

        return debitRepository.save(debit).toResponse(emptyList())
    }

    @Transactional
    fun ignore(debitId: UUID, request: BankDebitIgnoreRequest, accountId: UUID): BankDebitResponse {
        val debit = findDebit(debitId)
        check(debit.status == BankDebitStatus.UNMATCHED) { "Only payments waiting for review can be ignored" }

        debit.status = BankDebitStatus.IGNORED
        debit.subscriptionService = null
        debit.reviewNote = null
        debit.resolvedAt = Instant.now()
        debit.resolvedByAccountId = accountId
        if (request.rememberAmount) remember(debit, null, accountId)

        return debitRepository.save(debit).toResponse(emptyList())
    }

    /**
     * Takes a payment back out of its charge and returns it to review. The
     * charge is deleted when nothing is left of it. Refused once the charge is
     * split into ledger entries: undo that cost run first.
     */
    @Transactional
    fun undo(debitId: UUID): BankDebitUndoResult {
        val debit = findDebit(debitId)
        check(debit.status == BankDebitStatus.CHARGED) { "Only charged payments can be undone" }
        val charge = requireNotNull(debit.charge) { "Charged payment $debitId has no charge" }
        val chargeId = requireNotNull(charge.id)
        val serviceName = charge.subscriptionService?.name
        check(!ledgerEntryRepository.existsByChargeId(chargeId)) {
            "The ${charge.chargeMonth} $serviceName charge is already split into ledger entries. " +
                "Undo that cost run first."
        }

        val amountAfter = requireNotNull(charge.amount).subtract(requireNotNull(debit.amount))
        val others = debitRepository.findByChargeId(chargeId).filter { it.id != debit.id }
        val deleted = amountAfter.signum() <= 0
        debit.status = BankDebitStatus.UNMATCHED
        debit.charge = null
        debit.chargeMonth = null
        debit.matchMethod = null
        debit.resolvedAt = null
        debit.resolvedByAccountId = null
        debit.reviewNote = "Was charged to $serviceName for ${charge.chargeMonth}, then undone."
        val saved = debitRepository.saveAndFlush(debit)

        if (deleted) {
            others.forEach { release(it, "Its charge was removed when another payment in it was undone.") }
            chargeRepository.delete(charge)
        } else {
            charge.amount = amountAfter
            if (others.isNotEmpty()) charge.description = describe(others)
            chargeRepository.save(charge)
        }

        log.info("Undid bank charge {} of {} from charge {}", debit.externalId, debit.amount, chargeId)
        return BankDebitUndoResult(
            debit = saved.toResponse(ruleRepository.findAllWithService()),
            chargeId = chargeId,
            chargeDeleted = deleted,
            chargeAmountAfter = if (deleted) null else amountAfter
        )
    }

    /**
     * Returns every payment in a charge that is being deleted to review, so it
     * can be charged again. Called by [com.almonium.famsubbe.accounting.ChargeService].
     */
    @Transactional
    fun releaseCharge(chargeId: UUID) {
        debitRepository.findByChargeId(chargeId).forEach { release(it, "Its charge was deleted.") }
    }

    @Transactional
    fun createRule(request: BankMerchantRuleCreateRequest, accountId: UUID): BankMerchantRuleResponse {
        val merchant = request.merchant.trim().lowercase()
        require(merchant.isNotEmpty()) { "merchant is required" }
        val currencyCode = currencyCodeOf(request.currency)
        val amount = request.amount.setScale(2)
        check(ruleRepository.findByMerchantAndCurrencyCodeAndAmount(merchant, currencyCode, amount) == null) {
            "There is already a rule for $merchant ${formatOriginal(amount, currencyCode)}"
        }
        val service = request.subscriptionServiceId?.let { id ->
            subscriptionServiceRepository.findById(id)
                .orElseThrow { NoSuchElementException("Subscription service not found: $id") }
        }
        val rule = BankMerchantRule().apply {
            this.merchant = merchant
            this.currencyCode = currencyCode
            this.amount = amount
            subscriptionService = service
            ignored = service == null
            createdAt = Instant.now()
            createdByAccountId = accountId
        }
        return ruleRepository.save(rule).toResponse()
    }

    @Transactional
    fun deleteRule(ruleId: UUID): BankMerchantRuleResponse {
        val rule = ruleRepository.findById(ruleId)
            .orElseThrow { NoSuchElementException("Rule not found: $ruleId") }
        val response = rule.toResponse()
        ruleRepository.delete(rule)
        return response
    }

    /** The month a bill paid at [occurredAt] pays for: its own, or the next one late in the month. */
    fun chargeMonthOf(occurredAt: Instant): YearMonth {
        val date = BillingTime.dateOf(occurredAt)
        val month = YearMonth.from(date)
        return if (date.dayOfMonth >= monoProperties.chargeNextMonthFromDay) month.plusMonths(1) else month
    }

    /**
     * Why a payment cannot go into [service]'s charge for [month], or null when
     * it can. A charge already split into ledger entries is final either way. A
     * charge typed in by hand takes an admin's payment but not an automatic one,
     * since the hand-entered amount may already include it.
     */
    private fun blockerFor(service: SubscriptionService, month: YearMonth, automatic: Boolean): String? {
        val charge = chargeRepository.findBySubscriptionServiceAndChargeMonth(service, month) ?: return null
        val chargeId = requireNotNull(charge.id)
        if (ledgerEntryRepository.existsByChargeId(chargeId)) {
            return "The $month ${service.name} charge is already split into ledger entries. " +
                "Charge this to a later month, or undo that cost run first."
        }
        if (automatic && !debitRepository.existsByChargeId(chargeId)) {
            return "A $month ${service.name} charge of ₴${charge.amount} was entered by hand. " +
                "Add this payment to it if it is not already included, or ignore it."
        }
        return null
    }

    private fun addToCharge(
        debit: BankDebit,
        service: SubscriptionService,
        month: YearMonth,
        method: BankChargeMatchMethod,
        accountId: UUID?
    ) {
        val amount = requireNotNull(debit.amount)
        val existing = chargeRepository.findBySubscriptionServiceAndChargeMonth(service, month)
        val charge = if (existing == null) {
            chargeRepository.saveAndFlush(Charge().apply {
                subscriptionService = service
                this.amount = amount
                chargeMonth = month
                description = describe(listOf(debit))
            })
        } else {
            existing.amount = requireNotNull(existing.amount).add(amount)
            existing.description = listOfNotNull(existing.description, debit.label())
                .joinToString(" + ")
                .take(DESCRIPTION_LIMIT)
            chargeRepository.save(existing)
        }

        debit.status = BankDebitStatus.CHARGED
        debit.subscriptionService = service
        debit.charge = charge
        debit.chargeMonth = month
        debit.matchMethod = method
        debit.reviewNote = null
        debit.resolvedAt = Instant.now()
        debit.resolvedByAccountId = accountId

        log.info(
            "Charged bank payment {} of {} ({}) to {} for {} ({}): charge {} now {}",
            debit.externalId,
            amount,
            debit.originalText(),
            service.name,
            month,
            method,
            charge.id,
            charge.amount
        )
    }

    private fun release(debit: BankDebit, note: String) {
        debit.status = BankDebitStatus.UNMATCHED
        debit.charge = null
        debit.chargeMonth = null
        debit.matchMethod = null
        debit.resolvedAt = null
        debit.resolvedByAccountId = null
        debit.reviewNote = note
        debitRepository.save(debit)
    }

    /** Points this merchant's payments of this exact amount at [service], or at the ignore list when null. */
    private fun remember(debit: BankDebit, service: SubscriptionService?, accountId: UUID) {
        val merchant = requireNotNull(debit.merchant)
        val currencyCode = requireNotNull(debit.currencyCode)
        val amount = requireNotNull(debit.originalAmount)
        val rule = ruleRepository.findByMerchantAndCurrencyCodeAndAmount(merchant, currencyCode, amount)
            ?: BankMerchantRule().apply {
                this.merchant = merchant
                this.currencyCode = currencyCode
                this.amount = amount
                createdAt = Instant.now()
                createdByAccountId = accountId
            }
        rule.subscriptionService = service
        rule.ignored = service == null
        ruleRepository.save(rule)
    }

    private fun findDebit(id: UUID): BankDebit =
        debitRepository.findById(id).orElseThrow { NoSuchElementException("Card payment not found: $id") }

    private fun describe(debits: List<BankDebit>): String =
        debits.joinToString(" + ") { it.label() }.take(DESCRIPTION_LIMIT)

    private fun BankDebit.label() = "${description ?: merchant} ${originalText()}"

    private fun BankDebit.originalText() = formatOriginal(requireNotNull(originalAmount), requireNotNull(currencyCode))

    private fun BankDebit.toResponse(rules: List<BankMerchantRule>) = BankDebitResponse(
        id = requireNotNull(id),
        occurredAt = requireNotNull(occurredAt),
        amount = requireNotNull(amount),
        originalAmount = requireNotNull(originalAmount),
        currency = currencyOf(requireNotNull(currencyCode)),
        description = description,
        merchant = requireNotNull(merchant),
        status = status.name,
        subscriptionServiceId = subscriptionService?.id,
        subscriptionServiceName = subscriptionService?.name,
        chargeId = charge?.id,
        chargeMonth = chargeMonth,
        suggestedChargeMonth = chargeMonthOf(requireNotNull(occurredAt)),
        suggestedServiceId = if (status == BankDebitStatus.UNMATCHED) {
            subscriptionService?.id ?: rules.firstOrNull { it.merchant == merchant && !it.ignored }
                ?.subscriptionService?.id
        } else {
            null
        },
        matchMethod = matchMethod?.name,
        reviewNote = reviewNote,
        resolvedAt = resolvedAt
    )

    private fun BankMerchantRule.toResponse() = BankMerchantRuleResponse(
        id = requireNotNull(id),
        merchant = requireNotNull(merchant),
        currency = currencyOf(requireNotNull(currencyCode)),
        amount = requireNotNull(amount),
        subscriptionServiceId = subscriptionService?.id,
        subscriptionServiceName = subscriptionService?.name,
        ignored = ignored,
        learnedAutomatically = createdByAccountId == null,
        createdAt = requireNotNull(createdAt)
    )

    companion object {
        private const val MAX_LIMIT = 500
        private const val DESCRIPTION_LIMIT = 255

        /** How long to wait for a payment on hold to settle before taking its amount as final. */
        val HOLD_PATIENCE: Duration = Duration.ofDays(6)

        private val CURRENCIES: Map<Int, Currency> by lazy {
            Currency.getAvailableCurrencies().associateBy { it.numericCode }
        }

        /** The longest rule merchant contained in [description], so "openai" never claims "OpenRouter". */
        fun merchantOf(description: String?, rules: List<BankMerchantRule>): String? {
            val name = description?.lowercase() ?: return null
            return rules.mapNotNull { it.merchant }
                .filter { it in name }
                .maxByOrNull { it.length }
        }

        fun currencyOf(code: Int): String = CURRENCIES[code]?.currencyCode ?: code.toString()

        fun currencyCodeOf(letters: String): Int {
            val currency = runCatching { Currency.getInstance(letters.uppercase()) }.getOrNull()
            requireNotNull(currency) { "Unknown currency: $letters" }
            return currency.numericCode
        }

        fun formatOriginal(amount: BigDecimal, code: Int): String = when (code) {
            840 -> "$${amount.toPlainString()}"
            978 -> "€${amount.toPlainString()}"
            MonoStatementItem.UAH_CODE -> "₴${amount.toPlainString()}"
            else -> "${amount.toPlainString()} ${currencyOf(code)}"
        }

        private fun fractionDigits(code: Int): Int =
            CURRENCIES[code]?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2
    }
}
