package com.almonium.famsubbe.bank

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BankSenderIdentityTest {

    @Test
    fun `a card transfer from another person is keyed by the name after Від`() {
        val sender = BankSenderIdentity.of(item(3193, description = "Від: Поліна  Чернишова"))

        assertEquals(BankSenderIdentity("name:поліна чернишова", "Поліна Чернишова"), sender)
    }

    @Test
    fun `counterName wins over the description`() {
        val sender = BankSenderIdentity.of(
            item(60000, description = "Від: Nozhenko Andrii", counterName = "Nozhenko Andrii")
        )

        assertEquals("name:nozhenko andrii", sender?.key)
    }

    @Test
    fun `an IBAN transfer is keyed by the IBAN`() {
        val sender = BankSenderIdentity.of(
            item(10400, description = "FamSub", counterName = "Кравчук Роман", counterIban = "ua21 3052 9900 0002 6201 2345 6789 0")
        )

        assertEquals(BankSenderIdentity("iban:UA213052990000026201234567890", "Кравчук Роман"), sender)
    }

    @Test
    fun `own-card top-ups, refunds and debits are not payments`() {
        assertNull(BankSenderIdentity.of(item(2000000, description = "З Білої картки")))
        assertNull(BankSenderIdentity.of(item(117264, description = "Скасування. LIQPAY*BoKHlib")))
        assertNull(BankSenderIdentity.of(item(-3193, description = "Від: Поліна Чернишова")))
    }

    private fun item(
        amount: Long,
        description: String? = null,
        counterName: String? = null,
        counterIban: String? = null
    ) = MonoStatementItem(
        id = "op",
        time = 1_760_000_000,
        amount = amount,
        description = description,
        counterName = counterName,
        counterIban = counterIban
    )
}
