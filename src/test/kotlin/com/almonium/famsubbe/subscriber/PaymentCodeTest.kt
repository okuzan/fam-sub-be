package com.almonium.famsubbe.subscriber

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentCodeTest {

    @Test
    fun `a code is found in either text, whatever its case`() {
        assertEquals("7K2Q9X", PaymentCode.findIn(null, "FamSub FS-7K2Q9X"))
        assertEquals("7K2Q9X", PaymentCode.findIn("оплата famsub fs-7k2q9x", null))
    }

    @Test
    fun `text without a code, or with a code glued to other text, has none`() {
        assertNull(PaymentCode.findIn("Від: Поліна Чернишова", null))
        assertNull(PaymentCode.findIn("XFS-7K2Q9X", "FS-7K2Q9XZ"))
    }

    @Test
    fun `new codes avoid look-alike characters`() {
        repeat(200) {
            val code = PaymentCode.newCode()
            assertEquals(6, code.length)
            assertTrue(code.none { it in "01IO" })
            assertEquals(code, PaymentCode.findIn(PaymentCode.purposeFor(code)))
        }
    }
}
