package com.almonium.famsubbe.bank

/**
 * Who sent an incoming transfer, as far as the statement tells. The key is
 * what sender rules are stored under: the counterparty IBAN when the bank
 * gives one, otherwise the normalised name.
 */
data class BankSenderIdentity(
    val key: String,
    val name: String
) {
    companion object {
        /** Mono labels a transfer from another person "Від: <name>". */
        private const val FROM_PREFIX = "Від:"
        private val WHITESPACE = Regex("\\s+")

        /**
         * The sender of a credit that came from another person, or null for
         * anything else: debits, transfers between the owner's own cards
         * ("З Білої картки"), refunds, cashback.
         */
        fun of(item: MonoStatementItem): BankSenderIdentity? {
            if (item.amount <= 0) return null

            val iban = item.counterIban?.replace(WHITESPACE, "")?.uppercase()?.takeIf { it.isNotEmpty() }
            val name = item.counterName?.clean()
                ?: item.description?.trim()
                    ?.takeIf { it.startsWith(FROM_PREFIX) }
                    ?.removePrefix(FROM_PREFIX)
                    ?.clean()

            return when {
                iban != null -> BankSenderIdentity("iban:$iban", name ?: iban)
                name != null -> BankSenderIdentity("name:${name.lowercase()}", name)
                else -> null
            }
        }

        private fun String.clean(): String? = trim().replace(WHITESPACE, " ").takeIf { it.isNotEmpty() }
    }
}
