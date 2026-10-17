package com.almonium.famsubbe.bank

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

/**
 * Reads new incoming transfers, and card payments for subscriptions, from the
 * Monobank statement. Polling, not the
 * bank's webhook: Mono's webhook cannot carry the X-Requested-With header that
 * every write to this API requires, and a five-minute delay is fine here.
 *
 * Deliberately not transactional: each statement item is imported in its own
 * transaction, so one bad item cannot undo the others.
 */
@Service
class BankSyncService(
    private val properties: MonoProperties,
    private val monoClient: MonoClient,
    private val bankPaymentService: BankPaymentService,
    private val bankChargeService: BankChargeService,
    private val syncStateRepository: BankSyncStateRepository
) {
    private val log = LoggerFactory.getLogger(BankSyncService::class.java)
    private val lock = ReentrantLock()

    @Scheduled(
        fixedDelayString = $$"${app.bank.mono.sync-interval:PT5M}",
        initialDelayString = $$"${app.bank.mono.sync-initial-delay:PT1M}"
    )
    fun syncOnSchedule() {
        if (!properties.syncEnabled || !properties.configured) return
        try {
            val result = sync()
            if (result.chargedCount > 0 || result.chargeReviewCount > 0) {
                log.info(
                    "Bank sync read card payments: charged={}, needsReview={}",
                    result.chargedCount,
                    result.chargeReviewCount
                )
            }
            if (result.importedCount > 0) {
                log.info(
                    "Bank sync imported {} payments: applied={}, needsReview={}, ignored={}",
                    result.importedCount,
                    result.appliedCount,
                    result.needsReviewCount,
                    result.ignoredCount
                )
            }
        } catch (e: MonoRateLimitedException) {
            // The other blue/green slot, or an admin's "sync now", got there first.
            log.info("Bank sync skipped: rate limited by Monobank")
        } catch (e: Exception) {
            log.warn("Bank sync failed", e)
        }
    }

    fun sync(): BankSyncResult {
        check(properties.configured) { "Monobank is not configured: MONO_API_TOKEN is not set" }
        check(lock.tryLock()) { "A bank sync is already running" }
        try {
            return syncLocked()
        } finally {
            lock.unlock()
        }
    }

    fun status(): BankSyncStatusResponse {
        val state = properties.accountId.takeIf { it.isNotBlank() }
            ?.let { syncStateRepository.findById(it).orElse(null) }
        return BankSyncStatusResponse(
            configured = properties.configured,
            syncEnabled = properties.syncEnabled,
            syncedTo = state?.syncedTo,
            lastAttemptAt = state?.lastAttemptAt,
            lastSuccessAt = state?.lastSuccessAt,
            lastError = state?.lastError
        )
    }

    private fun syncLocked(): BankSyncResult {
        val now = Instant.now()
        val state = syncStateRepository.findById(properties.accountId).orElseGet {
            BankSyncState().apply {
                accountId = properties.accountId
                syncedTo = now.minus(properties.initialLookback)
            }
        }
        state.lastAttemptAt = now

        // Re-read well before the cursor: an operation can show up in the
        // statement after its own timestamp, and a card payment on hold is only
        // charged once it settles, which can take days. Duplicates are skipped.
        val from = requireNotNull(state.syncedTo).minus(OVERLAP)
        // A stale cursor catches up one maximal window per run.
        val to = minOf(now, from.plus(MAX_WINDOW))

        val items = try {
            fetch(from, to)
        } catch (e: Exception) {
            state.lastError = e.message ?: e.javaClass.simpleName
            syncStateRepository.save(state)
            throw e
        }

        val sorted = items.sortedBy { it.time }
        val outcomes = sorted.filter { it.amount > 0 }.map { item ->
            try {
                bankPaymentService.importMonoItem(item)
            } catch (e: DataIntegrityViolationException) {
                // Imported by the other instance between our check and our insert.
                BankImportOutcome.DUPLICATE
            }
        }
        val debitOutcomes = sorted.filter { it.amount < 0 }.map { item ->
            try {
                bankChargeService.importMonoDebit(item, now)
            } catch (e: DataIntegrityViolationException) {
                // The other instance got there first; the next sync re-reads it if
                // what collided was the charge rather than the payment itself.
                BankDebitOutcome.DUPLICATE
            }
        }

        state.syncedTo = to
        state.lastSuccessAt = now
        state.lastError = null
        syncStateRepository.save(state)

        return BankSyncResult(
            fetchedCount = items.size,
            importedCount = outcomes.count {
                it in setOf(BankImportOutcome.APPLIED, BankImportOutcome.NEEDS_REVIEW, BankImportOutcome.IGNORED)
            },
            appliedCount = outcomes.count { it == BankImportOutcome.APPLIED },
            needsReviewCount = outcomes.count { it == BankImportOutcome.NEEDS_REVIEW },
            ignoredCount = outcomes.count { it == BankImportOutcome.IGNORED },
            chargedCount = debitOutcomes.count { it == BankDebitOutcome.CHARGED },
            chargeReviewCount = debitOutcomes.count { it == BankDebitOutcome.NEEDS_REVIEW },
            syncedTo = to
        )
    }

    /** The whole window, paging backwards when Mono truncates it at its page limit. */
    private fun fetch(from: Instant, to: Instant): List<MonoStatementItem> {
        val items = mutableListOf<MonoStatementItem>()
        var windowEnd = to
        while (true) {
            val page = monoClient.statement(from, windowEnd)
            items += page
            if (page.size < MonoClient.STATEMENT_PAGE_LIMIT) break

            val oldest = Instant.ofEpochSecond(page.minOf { it.time })
            if (oldest >= windowEnd) break
            windowEnd = oldest
            Thread.sleep(RATE_LIMIT_PAUSE.toMillis())
        }
        return items.distinctBy { it.id }
    }

    private companion object {
        val OVERLAP: Duration = BankChargeService.HOLD_PATIENCE.plusDays(1)
        val MAX_WINDOW: Duration = Duration.ofDays(31)
        val RATE_LIMIT_PAUSE: Duration = Duration.ofSeconds(61)
    }
}
