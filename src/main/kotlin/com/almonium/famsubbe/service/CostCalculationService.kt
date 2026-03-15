package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.CostCalculationItemResult
import com.almonium.famsubbe.dto.CostCalculationResult
import com.almonium.famsubbe.entity.LedgerEntry
import com.almonium.famsubbe.repository.ChargeRepository
import com.almonium.famsubbe.repository.LedgerEntryRepository
import com.almonium.famsubbe.repository.MembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

@Service
@Transactional
class CostCalculationService(
    private val chargeRepository: ChargeRepository,
    private val membershipRepository: MembershipRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) {
    fun calculateAndRecordCosts(targetMonth: YearMonth, performedByAccountId: UUID): CostCalculationResult {
        val charges = chargeRepository.findByChargeMonth(targetMonth)
        val batchId = UUID.randomUUID()
        val calculatedAt = Instant.now()

        val items = mutableListOf<CostCalculationItemResult>()
        var entriesCreated = 0

        charges.forEach { charge ->
            if (ledgerEntryRepository.existsByChargeId(charge.id!!)) {
                throw IllegalStateException("Charge ${charge.id} already has ledger entries")
            }

            val activeMemberships = membershipRepository.findActiveByServiceAndMonth(
                charge.subscriptionService!!.id!!,
                targetMonth
            )

            if (activeMemberships.isEmpty()) {
                throw IllegalStateException("No active memberships for service ${charge.subscriptionService!!.name} in $targetMonth")
            }

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
                    this.recordedMonth = targetMonth
                    this.amount = splitAmounts[index]
                    this.participantCount = subscribers.size
                    this.calculatedAt = calculatedAt
                    this.calculationBatchId = batchId
                    this.notes = "calculated_by=$performedByAccountId"
                }
                ledgerEntryRepository.save(entry)
            }

            entriesCreated += subscribers.size
            items.add(
                CostCalculationItemResult(
                    chargeId = charge.id!!,
                    serviceId = charge.subscriptionService!!.id!!,
                    serviceName = charge.subscriptionService!!.name!!,
                    chargeAmount = charge.amount!!,
                    participantCount = subscribers.size,
                    success = true,
                    message = "Recorded ${subscribers.size} ledger entries"
                )
            )
        }

        return CostCalculationResult(
            calculationBatchId = batchId,
            targetMonth = targetMonth,
            chargesProcessed = charges.size,
            ledgerEntriesCreated = entriesCreated,
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
}
