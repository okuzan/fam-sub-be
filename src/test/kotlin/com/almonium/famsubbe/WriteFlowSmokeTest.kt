package com.almonium.famsubbe

import com.almonium.famsubbe.account.Account
import com.almonium.famsubbe.account.AccountRepository
import com.almonium.famsubbe.accounting.ChargeCreateRequest
import com.almonium.famsubbe.bank.BankImportOutcome
import com.almonium.famsubbe.bank.BankPaymentAlreadyRecordedRequest
import com.almonium.famsubbe.bank.BankPaymentService
import com.almonium.famsubbe.bank.BankTransactionStatus
import com.almonium.famsubbe.bank.MonoStatementItem
import com.almonium.famsubbe.accounting.ChargeService
import com.almonium.famsubbe.accounting.ChargeUpdateRequest
import com.almonium.famsubbe.accounting.CostCalculationService
import com.almonium.famsubbe.accounting.LedgerService
import com.almonium.famsubbe.accounting.RecoveryService
import com.almonium.famsubbe.finance.FinanceSummaryService
import com.almonium.famsubbe.invoice.InvoiceDeliveryService
import com.almonium.famsubbe.invoice.InvoiceGenerationRequest
import com.almonium.famsubbe.invoice.InvoiceService
import com.almonium.famsubbe.invoice.InvoiceStatus
import com.almonium.famsubbe.invoice.ManualInvoiceCreateRequest
import com.almonium.famsubbe.subscriber.SubscriberCreateRequest
import com.almonium.famsubbe.subscriber.SubscriberService
import com.almonium.famsubbe.subscriber.SubscriberStandingService
import com.almonium.famsubbe.subscriber.SubscriberUpdateRequest
import com.almonium.famsubbe.subscription.MembershipCreateRequest
import com.almonium.famsubbe.subscription.MembershipService
import com.almonium.famsubbe.subscription.MembershipUpdateRequest
import com.almonium.famsubbe.subscription.SubscriptionServiceCreateRequest
import com.almonium.famsubbe.subscription.SubscriptionServiceService
import com.almonium.famsubbe.subscription.SubscriptionServiceUpdateRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the admin's everyday writes through the real services and database,
 * outside any test transaction, the way a request does. It catches what unit
 * tests with mocked repositories cannot: a response read before Hibernate has
 * filled generated columns, a query that needs a flush, a lazy association
 * read after its session has closed. Needs Postgres; see AGENTS.md.
 */
@SpringBootTest
class WriteFlowSmokeTest {
    @Autowired lateinit var accounts: AccountRepository
    @Autowired lateinit var services: SubscriptionServiceService
    @Autowired lateinit var subscribers: SubscriberService
    @Autowired lateinit var memberships: MembershipService
    @Autowired lateinit var charges: ChargeService
    @Autowired lateinit var costs: CostCalculationService
    @Autowired lateinit var ledger: LedgerService
    @Autowired lateinit var invoices: InvoiceService
    @Autowired lateinit var standing: SubscriberStandingService
    @Autowired lateinit var finance: FinanceSummaryService
    @Autowired lateinit var bank: BankPaymentService
    @Autowired lateinit var delivery: InvoiceDeliveryService
    @Autowired lateinit var recovery: RecoveryService

    @Test
    fun `create, edit, split, invoice and pay end to end`() {
        val tag = UUID.randomUUID().toString().take(8)
        val admin = requireNotNull(accounts.save(Account().apply {
            email = "admin-$tag@example.com"
            passwordHash = UUID.randomUUID().toString()
        }).id)
        val month = YearMonth.of(2031, 1)

        val service = services.createService(SubscriptionServiceCreateRequest(name = "Smoke $tag", price = BigDecimal("300.00")))
        services.updateService(service.id, SubscriptionServiceUpdateRequest(name = "Smoke $tag", price = BigDecimal("310.00")))

        val alice = subscribers.createSubscriber(
            SubscriberCreateRequest(name = "Аліса", surname = "S$tag", email = "alice-$tag@example.com")
        )
        val bob = subscribers.createSubscriber(SubscriberCreateRequest(name = "Боб", email = "bob-$tag@example.com"))
        subscribers.updateSubscriber(
            bob.id,
            SubscriberUpdateRequest(name = "Боб", surname = "Smoke", email = "bob-$tag@example.com", balance = BigDecimal("50.00"))
        )
        assertTrue(subscribers.getAllSubscribers().any { it.id == alice.id })

        val membership = memberships.createMembership(MembershipCreateRequest(service.id, alice.id, month, null))
        memberships.updateMembership(membership.id, MembershipUpdateRequest(month, null))
        memberships.createMembership(MembershipCreateRequest(service.id, bob.id, month, month))

        val charge = charges.createCharge(ChargeCreateRequest(service.id, BigDecimal("300.00"), month, "smoke"))
        charges.updateCharge(charge.id, ChargeUpdateRequest(BigDecimal("310.00"), "smoke"))

        val calculation = costs.calculateAndRecordCosts(month, month, admin)
        ledger.getByCalculationBatch(requireNotNull(calculation.batchId))

        invoices.generateInvoices(InvoiceGenerationRequest(month, month, alice.id), admin)
        val manual = invoices.createManualInvoice(
            ManualInvoiceCreateRequest(bob.id, BigDecimal("12.00"), LocalDate.of(2031, 1, 5), "smoke"),
            admin
        )
        invoices.updateStatus(manual.id, InvoiceStatus.SENT)
        invoices.payFromBalance(manual.id)

        standing.getDetails(alice.id)
        finance.getSummary()
        assertEquals(1, invoices.getInvoices(alice.id).size)

        // A transfer under her name pays her invoice, then is undone and filed as already recorded.
        val owed = standing.getDetails(alice.id).totalAmountOwed
        val transfer = MonoStatementItem(
            id = "smoke-$tag",
            time = 1_900_000_000,
            amount = owed.movePointRight(2).toLong(),
            description = "Від: Аліса S$tag"
        )
        assertEquals(BankImportOutcome.APPLIED, bank.importMonoItem(transfer))
        val applied = bank.getTransactions(BankTransactionStatus.APPLIED, 500).single { it.senderName == "Аліса S$tag" }
        assertEquals(1, applied.settledInvoiceCount)

        val undone = bank.undo(applied.id)
        assertEquals(1, undone.reopenedInvoiceIds.size)
        assertEquals(0, owed.compareTo(standing.getDetails(alice.id).totalAmountOwed))

        val filed = bank.markAlreadyRecorded(applied.id, BankPaymentAlreadyRecordedRequest(alice.id, rememberSender = false), admin)
        assertTrue(filed.transaction.alreadyRecorded)
        bank.getTransactions(null, 500)

        // Credit that covers a new invoice pays it at generation; its receipt goes with the drafts; undo refunds it.
        val february = month.plusMonths(1)
        subscribers.updateSubscriber(
            alice.id,
            SubscriberUpdateRequest(name = "Аліса", surname = "S$tag", email = "alice-$tag@example.com", balance = BigDecimal("1000.00"))
        )
        charges.createCharge(ChargeCreateRequest(service.id, BigDecimal("310.00"), february, "smoke"))
        costs.calculateAndRecordCosts(february, february, admin)
        val run = invoices.generateInvoices(InvoiceGenerationRequest(february, february, alice.id), admin)
        assertEquals(1, run.paidFromBalanceCount)
        val covered = run.items.single()
        assertEquals(0, BigDecimal("690.00").compareTo(standing.getDetails(alice.id).balance))
        assertTrue(delivery.sendDrafts().items.any { it.invoiceId == covered.invoiceId })

        assertTrue(recovery.previewInvoiceRunUndo(run.runId).allowed)
        recovery.undoInvoiceRun(run.runId, admin, "smoke")
        assertEquals(0, BigDecimal("1000.00").compareTo(standing.getDetails(alice.id).balance))
    }
}
