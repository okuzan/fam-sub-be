package com.almonium.famsubbe.accounting

import com.almonium.famsubbe.subscription.Membership
import com.almonium.famsubbe.subscription.MembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.util.*

@Service
@Transactional
class CostCalculationService(
    private val chargeRepository: ChargeRepository,
    private val membershipRepository: MembershipRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val costCalculationBatchRepository: CostCalculationBatchRepository
) {
    fun calculateAndRecordCosts(
        fromMonth: YearMonth,
        toMonth: YearMonth,
        performedByAccountId: UUID
    ): CostCalculationResult {
        require(fromMonth <= toMonth) { "fromMonth cannot be after toMonth" }

        val monthsToProcess = generateMonths(fromMonth, toMonth)

        // Lock in chronological order so concurrent runs cannot calculate the same charge.
        val allChargesByMonth = monthsToProcess.associateWith { month ->
            chargeRepository.findByChargeMonthForUpdate(month)
        }
        val allCharges = allChargesByMonth.values.flatten()
        val recordedChargeIds = if (allCharges.isEmpty()) {
            emptySet()
        } else {
            ledgerEntryRepository.findRecordedChargeIds(allCharges.map { requireNotNull(it.id) })
        }
        val chargesByMonth = allChargesByMonth.mapValues { (_, charges) ->
            charges.filter { requireNotNull(it.id) !in recordedChargeIds }
        }
        val chargesToProcess = chargesByMonth.values.flatten()
        val calculatedAt = Instant.now()

        if (chargesToProcess.isEmpty()) {
            return CostCalculationResult(
                batchId = null,
                fromMonth = fromMonth,
                toMonth = toMonth,
                createdAt = calculatedAt,
                createdByAccountId = performedByAccountId,
                monthsProcessed = monthsToProcess.size,
                chargesProcessed = 0,
                chargesSkipped = allCharges.size,
                ledgerEntriesCreated = 0,
                items = emptyList()
            )
        }

        val membershipsCache = mutableMapOf<Pair<UUID, YearMonth>, List<Membership>>()

        for ((month, charges) in chargesByMonth) {
            for (charge in charges) {
                val chargeId = requireNotNull(charge.id) { "Charge id is null" }
                val service = requireNotNull(charge.subscriptionService) { "Charge $chargeId has no service" }
                val serviceId = requireNotNull(service.id) { "Service id is null for charge $chargeId" }

                val memberships = membershipsCache.getOrPut(serviceId to month) {
                    membershipRepository.findActiveByServiceAndMonth(serviceId, month)
                }

                check(memberships.isNotEmpty()) {
                    "No active memberships for service ${service.name} in $month"
                }
            }
        }

        val savedBatch = costCalculationBatchRepository.save(
            CostCalculationBatch().apply {
                this.fromMonth = fromMonth
                this.toMonth = toMonth
                this.createdByAccountId = performedByAccountId
                this.createdAt = calculatedAt
            }
        )

        val items = mutableListOf<CostCalculationItemResult>()
        val ledgerEntriesToSave = ArrayList<LedgerEntry>(1024)

        var totalChargesProcessed = 0
        var totalEntriesCreated = 0

        for ((month, charges) in chargesByMonth) {
            totalChargesProcessed += charges.size

            for (charge in charges) {
                val chargeId = requireNotNull(charge.id)
                val service = requireNotNull(charge.subscriptionService)
                val serviceId = requireNotNull(service.id)
                val amount = requireNotNull(charge.amount)

                val activeMemberships = membershipsCache.getValue(serviceId to month)

                val subscribers = activeMemberships
                    .map { requireNotNull(it.subscriber) }
                    .distinctBy { it.id }
                    .sortedBy { it.id.toString() }

                val splitAmounts = splitEvenly(amount, subscribers.size)

                subscribers.forEachIndexed { index, subscriber ->
                    ledgerEntriesToSave += LedgerEntry().apply {
                        this.charge = charge
                        this.subscriber = subscriber
                        this.subscriptionService = service
                        this.recordedMonth = month
                        this.amount = splitAmounts[index]
                        this.participantCount = subscribers.size
                        this.calculatedAt = calculatedAt
                        this.calculationBatch = savedBatch
                    }
                }

                totalEntriesCreated += subscribers.size

                items += CostCalculationItemResult(
                    chargeId = chargeId,
                    serviceId = serviceId,
                    serviceName = requireNotNull(service.name),
                    chargeAmount = amount,
                    participantCount = subscribers.size,
                    success = true,
                    message = "Recorded ${subscribers.size} ledger entries for $month"
                )
            }
        }

        if (ledgerEntriesToSave.isNotEmpty()) {
            ledgerEntryRepository.saveAll(ledgerEntriesToSave)
            ledgerEntryRepository.flush()
        }

        return CostCalculationResult(
            batchId = requireNotNull(savedBatch.id),
            fromMonth = fromMonth,
            toMonth = toMonth,
            createdAt = calculatedAt,
            createdByAccountId = performedByAccountId,
            monthsProcessed = monthsToProcess.size,
            chargesProcessed = totalChargesProcessed,
            chargesSkipped = recordedChargeIds.size,
            ledgerEntriesCreated = totalEntriesCreated,
            items = items
        )
    }

    private fun generateMonths(fromMonth: YearMonth, toMonth: YearMonth): List<YearMonth> {
        val result = mutableListOf<YearMonth>()
        var current = fromMonth
        while (current <= toMonth) {
            result += current
            current = current.plusMonths(1)
        }
        return result
    }

    private fun splitEvenly(total: BigDecimal, participants: Int): List<BigDecimal> {
        require(participants > 0) { "participants must be > 0" }

        val totalCents = total
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()

        val baseCents = totalCents / participants
        val remainderCents = (totalCents % participants).toInt()

        val result = MutableList(participants) { baseCents }

        for (i in 0 until remainderCents) {
            result[i] = result[i] + 1
        }

        return result.map { BigDecimal.valueOf(it, 2) }
    }

    fun getSuggestedCalculationPeriod(): CostCalculationSuggestion {
        val currentMonth = YearMonth.now()
        val latestBatch = costCalculationBatchRepository.findFirstByUndoneAtIsNullOrderByToMonthDesc()

        val suggestedFromMonth = latestBatch?.toMonth?.plusMonths(1) ?: currentMonth
        val suggestedToMonth = maxOf(currentMonth, suggestedFromMonth)

        return CostCalculationSuggestion(
            lastCalculatedToMonth = latestBatch?.toMonth,
            suggestedFromMonth = suggestedFromMonth,
            suggestedToMonth = suggestedToMonth
        )
    }

    @Transactional(readOnly = true)
    fun getRecentCalculationBatches(): List<CostCalculationBatch> {
        return costCalculationBatchRepository.findAllVisibleOrderByCreatedAtDesc().take(10)
    }
}
