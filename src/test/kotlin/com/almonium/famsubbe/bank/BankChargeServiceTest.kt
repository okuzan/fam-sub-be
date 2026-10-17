package com.almonium.famsubbe.bank

import com.almonium.famsubbe.accounting.Charge
import com.almonium.famsubbe.accounting.ChargeRepository
import com.almonium.famsubbe.accounting.LedgerEntryRepository
import com.almonium.famsubbe.subscription.SubscriptionService
import com.almonium.famsubbe.subscription.SubscriptionServiceRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BankChargeServiceTest {
    private val debitRepository = mock(BankDebitRepository::class.java)
    private val ruleRepository = mock(BankMerchantRuleRepository::class.java)
    private val chargeRepository = mock(ChargeRepository::class.java)
    private val ledgerEntryRepository = mock(LedgerEntryRepository::class.java)
    private val subscriptionServiceRepository = mock(SubscriptionServiceRepository::class.java)
    private val service = BankChargeService(
        debitRepository,
        ruleRepository,
        chargeRepository,
        ledgerEntryRepository,
        subscriptionServiceRepository,
        MonoProperties(token = "t", accountId = "acc")
    )

    private val chatGpt = subscriptionService("ChatGPT")
    private val claude = subscriptionService("Claude")
    private val now: Instant = kyiv(2026, 11, 20, 12)

    init {
        `when`(ruleRepository.findAllWithService()).thenReturn(
            listOf(
                rule("openai", 840, "100.00", chatGpt),
                rule("claude", 840, "200.00", claude),
                rule("claude", 840, "20.00", null)
            )
        )
        `when`(debitRepository.saveAndFlush(any(BankDebit::class.java))).thenAnswer {
            it.getArgument<BankDebit>(0).apply { if (id == null) id = UUID.randomUUID() }
        }
        `when`(debitRepository.save(any(BankDebit::class.java))).thenAnswer { it.getArgument<BankDebit>(0) }
        `when`(chargeRepository.saveAndFlush(any(Charge::class.java))).thenAnswer {
            it.getArgument<Charge>(0).apply { id = UUID.randomUUID() }
        }
        `when`(chargeRepository.save(any(Charge::class.java))).thenAnswer { it.getArgument<Charge>(0) }
    }

    @Test
    fun `a bill matching a rule creates the month's charge`() {
        val outcome = service.importMonoDebit(item("OpenAI", 4_503_08, 100_00, time = kyiv(2026, 11, 10, 0)), now)

        assertEquals(BankDebitOutcome.CHARGED, outcome)
        val charge = savedCharge()
        assertEquals(chatGpt, charge.subscriptionService)
        assertEquals(YearMonth.of(2026, 11), charge.chargeMonth)
        assertEquals(BigDecimal("4503.08"), charge.amount)
        assertEquals("OpenAI $100.00", charge.description)
    }

    @Test
    fun `a second bill in the same month is added to the charge it started`() {
        val existing = Charge().apply {
            id = UUID.randomUUID()
            subscriptionService = chatGpt
            chargeMonth = YearMonth.of(2026, 11)
            amount = BigDecimal("4503.08")
            description = "OpenAI $100.00"
        }
        `when`(chargeRepository.findBySubscriptionServiceAndChargeMonth(chatGpt, YearMonth.of(2026, 11)))
            .thenReturn(existing)
        `when`(debitRepository.existsByChargeId(requireNotNull(existing.id))).thenReturn(true)

        val outcome = service.importMonoDebit(item("OpenAI", 4_490_00, 100_00, time = kyiv(2026, 11, 12, 9)), now)

        assertEquals(BankDebitOutcome.CHARGED, outcome)
        assertEquals(BigDecimal("8993.08"), existing.amount)
        assertEquals("OpenAI $100.00 + OpenAI $100.00", existing.description)
    }

    @Test
    fun `a bill late in the month pays for the next one`() {
        service.importMonoDebit(item("Claude", 8_900_00, 200_00, time = kyiv(2026, 10, 30, 21)), now)

        assertEquals(YearMonth.of(2026, 11), savedCharge().chargeMonth)
    }

    @Test
    fun `a known merchant with an unknown amount waits for review`() {
        val outcome = service.importMonoDebit(item("Claude", 4_697_61, 103_93), now)

        assertEquals(BankDebitOutcome.NEEDS_REVIEW, outcome)
        val debit = savedDebit()
        assertEquals(BankDebitStatus.UNMATCHED, debit.status)
        assertTrue(requireNotNull(debit.reviewNote).contains("$103.93"))
        verify(chargeRepository, never()).saveAndFlush(any(Charge::class.java))
    }

    @Test
    fun `an ignore rule files the bill as ignored`() {
        assertEquals(BankDebitOutcome.IGNORED, service.importMonoDebit(item("Claude", 900_00, 20_00), now))
        assertEquals(BankDebitStatus.IGNORED, savedDebit().status)
    }

    @Test
    fun `payments to unknown merchants and incoming money are not stored`() {
        assertEquals(BankDebitOutcome.NOT_A_SUBSCRIPTION, service.importMonoDebit(item("Сільпо", 500_00, 500_00, 980), now))
        assertEquals(BankDebitOutcome.NOT_A_SUBSCRIPTION, service.importMonoDebit(item("OpenRouter", 488_16, 10_80), now))
        assertEquals(
            BankDebitOutcome.NOT_A_SUBSCRIPTION,
            service.importMonoDebit(item("OpenAI", -100_00, -100_00), now)
        )
        verify(debitRepository, never()).saveAndFlush(any(BankDebit::class.java))
    }

    @Test
    fun `a bill on hold waits until it settles, unless it has been on hold too long`() {
        val fresh = item("OpenAI", 4_503_08, 100_00, time = now.minusSeconds(3600), hold = true)
        assertEquals(BankDebitOutcome.PENDING, service.importMonoDebit(fresh, now))

        val stale = item("OpenAI", 4_503_08, 100_00, time = now.minus(BankChargeService.HOLD_PATIENCE).minusSeconds(60), hold = true)
        assertEquals(BankDebitOutcome.CHARGED, service.importMonoDebit(stale, now))
    }

    @Test
    fun `a charge typed in by hand is not added to on its own`() {
        val manual = Charge().apply {
            id = UUID.randomUUID()
            subscriptionService = chatGpt
            chargeMonth = YearMonth.of(2026, 11)
            amount = BigDecimal("905.00")
        }
        `when`(chargeRepository.findBySubscriptionServiceAndChargeMonth(chatGpt, YearMonth.of(2026, 11))).thenReturn(manual)

        val outcome = service.importMonoDebit(item("OpenAI", 4_503_08, 100_00, time = kyiv(2026, 11, 10, 0)), now)

        assertEquals(BankDebitOutcome.NEEDS_REVIEW, outcome)
        assertEquals(BigDecimal("905.00"), manual.amount)
        assertTrue(requireNotNull(savedDebit().reviewNote).contains("entered by hand"))
    }

    @Test
    fun `a charge already split is never changed, even by an admin`() {
        val split = Charge().apply {
            id = UUID.randomUUID()
            subscriptionService = chatGpt
            chargeMonth = YearMonth.of(2026, 11)
            amount = BigDecimal("4503.08")
        }
        `when`(chargeRepository.findBySubscriptionServiceAndChargeMonth(chatGpt, YearMonth.of(2026, 11))).thenReturn(split)
        `when`(ledgerEntryRepository.existsByChargeId(requireNotNull(split.id))).thenReturn(true)
        val debit = storedDebit(time = kyiv(2026, 11, 10, 0))
        `when`(subscriptionServiceRepository.findById(requireNotNull(chatGpt.id))).thenReturn(Optional.of(chatGpt))

        assertFailsWith<IllegalStateException> {
            service.charge(requireNotNull(debit.id), BankDebitChargeRequest(requireNotNull(chatGpt.id)), UUID.randomUUID())
        }
        assertEquals(BigDecimal("4503.08"), split.amount)
    }

    @Test
    fun `an admin charge can remember the amount as a new rule`() {
        val debit = storedDebit(time = kyiv(2026, 11, 10, 0), original = "103.93", merchant = "claude")
        `when`(subscriptionServiceRepository.findById(requireNotNull(claude.id))).thenReturn(Optional.of(claude))

        val response = service.charge(
            requireNotNull(debit.id),
            BankDebitChargeRequest(requireNotNull(claude.id), rememberAmount = true),
            UUID.randomUUID()
        )

        assertEquals(BankDebitStatus.CHARGED.name, response.status)
        assertEquals(YearMonth.of(2026, 11), response.chargeMonth)
        verify(ruleRepository).save(org.mockito.ArgumentMatchers.argThat<BankMerchantRule> {
            it.merchant == "claude" && it.amount == BigDecimal("103.93") && it.subscriptionService == claude
        })
    }

    @Test
    fun `undoing the only payment in a charge deletes the charge`() {
        val charge = Charge().apply {
            id = UUID.randomUUID()
            subscriptionService = chatGpt
            chargeMonth = YearMonth.of(2026, 11)
            amount = BigDecimal("4503.08")
        }
        val debit = storedDebit(time = kyiv(2026, 11, 10, 0)).apply {
            status = BankDebitStatus.CHARGED
            this.charge = charge
            chargeMonth = charge.chargeMonth
        }
        `when`(debitRepository.findByChargeId(requireNotNull(charge.id))).thenReturn(listOf(debit))

        val result = service.undo(requireNotNull(debit.id))

        assertTrue(result.chargeDeleted)
        assertEquals(BankDebitStatus.UNMATCHED, debit.status)
        assertNull(debit.charge)
        verify(chargeRepository).delete(charge)
    }

    @Test
    fun `undoing one of two payments leaves the rest of the charge`() {
        val charge = Charge().apply {
            id = UUID.randomUUID()
            subscriptionService = chatGpt
            chargeMonth = YearMonth.of(2026, 11)
            amount = BigDecimal("9006.16")
        }
        val debit = storedDebit(time = kyiv(2026, 11, 10, 0)).apply {
            status = BankDebitStatus.CHARGED
            this.charge = charge
        }
        val other = storedDebit(time = kyiv(2026, 11, 12, 0)).apply {
            status = BankDebitStatus.CHARGED
            this.charge = charge
        }
        `when`(debitRepository.findByChargeId(requireNotNull(charge.id))).thenReturn(listOf(debit, other))

        val result = service.undo(requireNotNull(debit.id))

        assertEquals(false, result.chargeDeleted)
        assertEquals(BigDecimal("4503.08"), charge.amount)
        assertEquals(BankDebitStatus.CHARGED, other.status)
        verify(chargeRepository, never()).delete(charge)
    }

    @Test
    fun `the longest merchant wins, so a rule never claims a lookalike`() {
        val rules = listOf(rule("open", 840, "1.00", chatGpt), rule("openai", 840, "1.00", chatGpt))
        assertEquals("openai", BankChargeService.merchantOf("OpenAI", rules))
        assertNull(BankChargeService.merchantOf("Google", rules))
    }

    private fun savedCharge(): Charge {
        val captor = org.mockito.ArgumentCaptor.forClass(Charge::class.java)
        verify(chargeRepository).saveAndFlush(captor.capture())
        return captor.value
    }

    private fun savedDebit(): BankDebit {
        val captor = org.mockito.ArgumentCaptor.forClass(BankDebit::class.java)
        verify(debitRepository).saveAndFlush(captor.capture())
        return captor.value
    }

    private fun storedDebit(
        time: Instant,
        original: String = "100.00",
        merchant: String = "openai"
    ): BankDebit {
        val debit = BankDebit().apply {
            id = UUID.randomUUID()
            externalId = "op-${UUID.randomUUID()}"
            accountId = "acc"
            occurredAt = time
            amount = BigDecimal("4503.08")
            originalAmount = BigDecimal(original)
            currencyCode = 840
            description = merchant.replaceFirstChar { it.uppercase() }
            this.merchant = merchant
            createdAt = time
        }
        `when`(debitRepository.findById(requireNotNull(debit.id))).thenReturn(Optional.of(debit))
        return debit
    }

    private fun item(
        description: String,
        uahKopiyky: Long,
        originalMinor: Long,
        currencyCode: Int = 840,
        time: Instant = now.minusSeconds(86_400),
        hold: Boolean = false
    ) = MonoStatementItem(
        id = "op-${UUID.randomUUID()}",
        time = time.epochSecond,
        amount = -uahKopiyky,
        description = description,
        operationAmount = -originalMinor,
        currencyCode = currencyCode,
        mcc = 5734,
        hold = hold
    )

    private fun rule(merchant: String, currencyCode: Int, amount: String, target: SubscriptionService?) =
        BankMerchantRule().apply {
            id = UUID.randomUUID()
            this.merchant = merchant
            this.currencyCode = currencyCode
            this.amount = BigDecimal(amount)
            subscriptionService = target
            ignored = target == null
            createdAt = Instant.EPOCH
        }

    private fun subscriptionService(name: String) = SubscriptionService().apply {
        id = UUID.randomUUID()
        this.name = name
        price = BigDecimal.ONE
    }

    private fun kyiv(year: Int, month: Int, day: Int, hour: Int): Instant =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, java.time.ZoneId.of("Europe/Kyiv")).toInstant()
}
