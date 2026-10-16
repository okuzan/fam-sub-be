package com.almonium.famsubbe.subscriber

import java.security.SecureRandom
import java.util.*

/**
 * The code in a payment purpose, "FamSub FS-7K2Q9X". The pay page prefills it,
 * so a transfer that carries it can be matched to its subscriber without
 * knowing the sender.
 */
object PaymentCode {
    const val PREFIX = "FS-"

    // No 0/O or 1/I, so a code read aloud or retyped survives.
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 6
    private val PATTERN = Regex("""\bFS-([A-Z0-9]{$LENGTH})\b""", RegexOption.IGNORE_CASE)
    private val random = SecureRandom()

    fun newCode(): String = (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun newPayToken(): String = UUID.randomUUID().toString().replace("-", "")

    fun purposeFor(code: String): String = "FamSub $PREFIX$code"

    /** The first payment code found in any of [texts], upper-cased. */
    fun findIn(vararg texts: String?): String? =
        texts.firstNotNullOfOrNull { text -> text?.let { PATTERN.find(it)?.groupValues?.get(1)?.uppercase() } }
}
