package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.CostCalculationItemResult
import com.almonium.famsubbe.dto.CostCalculationResult
import com.almonium.famsubbe.dto.CostCalculationSuggestion
import com.almonium.famsubbe.entity.CostCalculationBatch
import com.almonium.famsubbe.entity.LedgerEntry
import com.almonium.famsubbe.repository.ChargeRepository
import com.almonium.famsubbe.repository.CostCalculationBatchRepository
import com.almonium.famsubbe.repository.LedgerEntryRepository
import com.almonium.famsubbe.repository.MembershipRepository
import org.springframework.data.domain.Sort
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

        // load all charges once per month
        val chargesByMonth = monthsToProcess.associateWith { month ->
            chargeRepository.findByChargeMonth(month).also { charges ->
                check(charges.isNotEmpty()) { "No charges found for $month" }
            }
        }

        // validation + membership cache
        val membershipsCache = mutableMapOf<Pair<UUID, YearMonth>, List<com.almonium.famsubbe.entity.Membership>>()

        for ((month, charges) in chargesByMonth) {
            for (charge in charges) {
                val chargeId = requireNotNull(charge.id) { "Charge id is null" }
                val service = requireNotNull(charge.subscriptionService) { "Charge $chargeId has no service" }
                val serviceId = requireNotNull(service.id) { "Service id is null for charge $chargeId" }

                check(!ledgerEntryRepository.existsByChargeId(chargeId)) {
                    "Charge $chargeId for $month already has ledger entries"
                }

                val memberships = membershipsCache.getOrPut(serviceId to month) {
                    membershipRepository.findActiveByServiceAndMonth(serviceId, month)
                }

                check(memberships.isNotEmpty()) {
                    "No active memberships for service ${service.name} in $month"
                }
            }
        }

        val calculatedAt = Instant.now()

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
                        this.notes = "calculated_by=$performedByAccountId"
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
        val base = total.divide(BigDecimal(participants), 2, RoundingMode.DOWN)
        val result = MutableList(participants) { base }
        val assigned = base.multiply(BigDecimal(participants))
        val remainder = total.subtract(assigned)
        result[participants - 1] = result[participants - 1].add(remainder)
        return result
    }

    fun getSuggestedCalculationPeriod(): CostCalculationSuggestion {
        val currentMonth = YearMonth.now()
        val latestBatch = costCalculationBatchRepository.findFirstByOrderByToMonthDesc()

        return if (latestBatch != null) {
            val suggestedFromMonth = latestBatch.toMonth!!.plusMonths(1)
            CostCalculationSuggestion(
                lastCalculatedToMonth = latestBatch.toMonth,
                suggestedFromMonth = suggestedFromMonth,
                suggestedToMonth = currentMonth
            )
        } else {
            CostCalculationSuggestion(
                lastCalculatedToMonth = null,
                suggestedFromMonth = currentMonth,
                suggestedToMonth = currentMonth
            )
        }
    }

    @Transactional(readOnly = true)
    fun getRecentCalculationBatches(): List<CostCalculationBatch> {
        return costCalculationBatchRepository.findAll(
            Sort.by(Sort.Direction.DESC, "createdAt")
        ).take(10) // Return last 10 batches
    }
}
