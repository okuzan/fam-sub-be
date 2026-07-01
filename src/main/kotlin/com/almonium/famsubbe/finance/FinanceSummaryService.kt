package com.almonium.famsubbe.finance

import com.almonium.famsubbe.invoice.Invoice
import com.almonium.famsubbe.invoice.InvoiceRepository
import com.almonium.famsubbe.invoice.InvoiceStatus
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

@Service
class FinanceSummaryService(
    private val subscriberRepository: SubscriberRepository,
    private val invoiceRepository: InvoiceRepository
) {
    @Transactional(readOnly = true)
    fun getSummary(): FinanceSummaryResponse {
        val subscribers = subscriberRepository.findAll()
        val unpaidInvoices = invoiceRepository.findByStatusInWithSubscriber(UNPAID_STATUSES)
        return FinanceSummaryCalculator.calculate(subscribers, unpaidInvoices, Instant.now())
    }

    private companion object {
        val UNPAID_STATUSES = listOf(InvoiceStatus.DRAFT, InvoiceStatus.SENT)
    }
}

object FinanceSummaryCalculator {
    fun calculate(
        subscribers: List<Subscriber>,
        unpaidInvoices: List<Invoice>,
        generatedAt: Instant
    ): FinanceSummaryResponse {
        val invoicesBySubscriberId = unpaidInvoices.groupBy { requireNotNull(it.subscriber?.id) }
        val totalCabinetBalance = subscribers.sumOf { it.balance.orZero() }

        val positions = subscribers.map { subscriber ->
            val invoices = invoicesBySubscriberId[requireNotNull(subscriber.id)].orEmpty()
            val unpaidAmount = invoices.sumOf { it.totalAmount.orZero() }
            val balance = subscriber.balance.orZero()
            SubscriberPosition(
                subscriber = subscriber,
                invoiceCount = invoices.size,
                unpaidAmount = unpaidAmount,
                balance = balance,
                appliedBalance = balance.coerceAtLeast(BigDecimal.ZERO).coerceAtMost(unpaidAmount),
                netDebt = (unpaidAmount - balance).coerceAtLeast(BigDecimal.ZERO),
                unappliedCredit = (balance - unpaidAmount).coerceAtLeast(BigDecimal.ZERO)
            )
        }

        val netOutstandingDebt = positions.sumOf { it.netDebt }
        val debtors = positions
            .filter { it.netDebt > BigDecimal.ZERO }
            .sortedWith(compareByDescending<SubscriberPosition> { it.netDebt }.thenBy { it.subscriber.name })
        val unpaidInvoiceAmount = unpaidInvoices.sumOf { it.totalAmount.orZero() }
        val unappliedCredit = positions.sumOf { it.unappliedCredit }
        val balanceAppliedToDebt = positions.sumOf { it.appliedBalance }
        val averageDebt = if (debtors.isEmpty()) {
            BigDecimal.ZERO
        } else {
            netOutstandingDebt.divide(BigDecimal(debtors.size), 2, RoundingMode.HALF_UP)
        }

        return FinanceSummaryResponse(
            generatedAt = generatedAt,
            totalCabinetBalance = totalCabinetBalance,
            unpaidInvoiceCount = unpaidInvoices.size,
            unpaidInvoiceAmount = unpaidInvoiceAmount,
            balanceAppliedToDebt = balanceAppliedToDebt,
            netOutstandingDebt = netOutstandingDebt,
            debtorCount = debtors.size,
            averageDebt = averageDebt,
            unappliedCredit = unappliedCredit,
            oldestUnpaidInvoice = unpaidInvoices
                .minByOrNull { requireNotNull(it.createdAt) }
                ?.toOldestInvoice(generatedAt),
            debtors = debtors.map { it.toResponse(netOutstandingDebt) }
        )
    }

    private fun SubscriberPosition.toResponse(totalDebt: BigDecimal) =
        DebtorFinanceResponse(
            subscriberId = requireNotNull(subscriber.id),
            subscriberName = requireNotNull(subscriber.name),
            unpaidInvoiceCount = invoiceCount,
            unpaidInvoiceAmount = unpaidAmount,
            cabinetBalance = balance,
            netDebt = netDebt,
            debtSharePercent = if (totalDebt > BigDecimal.ZERO) {
                netDebt.multiply(BigDecimal(100)).divide(totalDebt, 2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
        )

    private fun Invoice.toOldestInvoice(generatedAt: Instant): OldestUnpaidInvoiceResponse {
        val invoiceCreatedAt = requireNotNull(createdAt)
        val invoiceSubscriber = requireNotNull(subscriber)
        return OldestUnpaidInvoiceResponse(
            invoiceId = requireNotNull(id),
            subscriberId = requireNotNull(invoiceSubscriber.id),
            subscriberName = requireNotNull(invoiceSubscriber.name),
            amount = totalAmount.orZero(),
            createdAt = invoiceCreatedAt,
            ageDays = Duration.between(invoiceCreatedAt, generatedAt).toDays().coerceAtLeast(0)
        )
    }

    private fun BigDecimal?.orZero() = this ?: BigDecimal.ZERO

    private data class SubscriberPosition(
        val subscriber: Subscriber,
        val invoiceCount: Int,
        val unpaidAmount: BigDecimal,
        val balance: BigDecimal,
        val appliedBalance: BigDecimal,
        val netDebt: BigDecimal,
        val unappliedCredit: BigDecimal
    )
}
