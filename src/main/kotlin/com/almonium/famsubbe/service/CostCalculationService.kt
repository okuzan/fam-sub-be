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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
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
        // Validate date range
        if (fromMonth > toMonth) {
            throw IllegalArgumentException("fromMonth cannot be after toMonth")
        }
        
        // Validate all months in range have charges before creating batch
        val monthsToProcess = mutableListOf<YearMonth>()
        var currentMonth = fromMonth
        while (currentMonth <= toMonth) {
            val charges = chargeRepository.findByChargeMonth(currentMonth)
            if (charges.isEmpty()) {
                throw IllegalStateException("No charges found for $currentMonth")
            }
            
            // Validate charges for this month
            charges.forEach { charge ->
                if (ledgerEntryRepository.existsByChargeId(charge.id!!)) {
                    throw IllegalStateException("Charge ${charge.id} for $currentMonth already has ledger entries")
                }
                
                val activeMemberships = membershipRepository.findActiveByServiceAndMonth(
                    charge.subscriptionService!!.id!!,
                    currentMonth
                )
                if (activeMemberships.isEmpty()) {
                    throw IllegalStateException("No active memberships for service ${charge.subscriptionService!!.name} in $currentMonth")
                }
            }
            
            monthsToProcess.add(currentMonth)
            currentMonth = currentMonth.plusMonths(1)
        }
        
        // Create batch only after all validation passes
        val batch = CostCalculationBatch().apply {
            this.fromMonth = fromMonth
            this.toMonth = toMonth
            this.createdByAccountId = performedByAccountId
        }
        val savedBatch = costCalculationBatchRepository.saveAndFlush(batch)
        val calculatedAt = savedBatch.createdAt!!
        
        val items = mutableListOf<CostCalculationItemResult>()
        var totalChargesProcessed = 0
        var totalEntriesCreated = 0
        
        // Process each month
        monthsToProcess.forEach { month ->
            val charges = chargeRepository.findByChargeMonth(month)
            totalChargesProcessed += charges.size
            
            charges.forEach { charge ->
                val activeMemberships = membershipRepository.findActiveByServiceAndMonth(
                    charge.subscriptionService!!.id!!,
                    month
                )
                
                val subscribers = activeMemberships
                    .map { it.subscriber!! }
                    .distinctBy { it.id }
                    .sortedBy { it.id.toString() }
                
                val splitAmounts = splitEvenly(charge.amount!!, subscribers.size)
                
                subscribers.forEachIndexed { index, subscriber ->
                    val entry = LedgerEntry().apply {
                        this.charge = charge
                        this.subscriber = subscriber
                        this.subscriptionService = charge.subscriptionService
                        this.recordedMonth = month
                        this.amount = splitAmounts[index]
                        this.participantCount = subscribers.size
                        this.calculatedAt = calculatedAt
                        this.calculationBatch = savedBatch
                        this.notes = "calculated_by=$performedByAccountId"
                    }
                    ledgerEntryRepository.save(entry)
                }
                
                totalEntriesCreated += subscribers.size
                
                items.add(
                    CostCalculationItemResult(
                        chargeId = charge.id!!,
                        serviceId = charge.subscriptionService!!.id!!,
                        serviceName = charge.subscriptionService!!.name!!,
                        chargeAmount = charge.amount!!,
                        participantCount = subscribers.size,
                        success = true,
                        message = "Recorded ${subscribers.size} ledger entries for $month"
                    )
                )
            }
        }
        
        return CostCalculationResult(
            batchId = savedBatch.id!!,
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
        
        // Find the latest calculated batch
        val latestBatch = costCalculationBatchRepository.findFirstByOrderByToMonthDesc()
        
        return if (latestBatch != null) {
            val suggestedFromMonth = latestBatch.toMonth!!.plusMonths(1)
            CostCalculationSuggestion(
                lastCalculatedToMonth = latestBatch.toMonth,
                suggestedFromMonth = suggestedFromMonth,
                suggestedToMonth = currentMonth
            )
        } else {
            // No previous calculations, suggest from current month
            CostCalculationSuggestion(
                lastCalculatedToMonth = null,
                suggestedFromMonth = currentMonth,
                suggestedToMonth = currentMonth
            )
        }
    }
}
