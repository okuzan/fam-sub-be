package com.almonium.famsubbe.payment

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NbuQrCodeTest {

    @Test
    fun `the link carries the version 002 fields in order, UTF-8, Base64URL without padding`() {
        val link = NbuQrCode.link(
            recipientName = "Кузан Олег",
            iban = "UA58 3220 0100 0002 6205 3630 0778 8",
            recipientCode = "1234567890",
            purpose = "FamSub FS-7K2Q9X",
            amount = BigDecimal("31.93")
        )

        assertTrue(link.startsWith("https://bank.gov.ua/qr/"))
        val encoded = link.removePrefix("https://bank.gov.ua/qr/")
        assertFalse('=' in encoded || '+' in encoded || '/' in encoded)

        val fields = String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8).split("\n")
        assertEquals(
            listOf(
                "BCD", "002", "1", "UCT", "",
                "Кузан Олег", "UA583220010000026205363007788", "UAH31.93", "1234567890",
                "", "", "FamSub FS-7K2Q9X", "", ""
            ),
            fields
        )
    }

    @Test
    fun `no amount leaves the field empty for the payer`() {
        val link = NbuQrCode.link("Кузан Олег", "UA583220010000026205363007788", "1234567890", "FamSub", null)
        val fields = String(Base64.getUrlDecoder().decode(link.removePrefix("https://bank.gov.ua/qr/"))).split("\n")

        assertEquals("", fields[7])
    }

    @Test
    fun `amounts are as short as the format allows`() {
        assertEquals("3", NbuQrCode.formatAmount(BigDecimal("3.00")))
        assertEquals("31.90", NbuQrCode.formatAmount(BigDecimal("31.9")))
        assertEquals("1500", NbuQrCode.formatAmount(BigDecimal("1500.00")))
    }

    @Test
    fun `the recipient tax number is mandatory`() {
        assertThrows<IllegalArgumentException> {
            NbuQrCode.link("Кузан Олег", "UA583220010000026205363007788", " ", "FamSub", BigDecimal.TEN)
        }
    }
}
