package com.almonium.famsubbe.bank

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal
import java.time.Duration

/**
 * The Monobank personal API, read with the account owner's own token. Only
 * the statement of [accountId] is read; nothing is ever sent to the bank.
 */
@ConfigurationProperties(prefix = "app.bank.mono")
data class MonoProperties(
    val apiUrl: String = "https://api.monobank.ua",
    val token: String = "",
    val accountId: String = "",
    /** Poll the statement on a schedule. Off in dev: dev and prod share the token's rate limit. */
    val syncEnabled: Boolean = false,
    /** How far back the very first sync reads. Zero means only payments from then on. */
    val initialLookback: Duration = Duration.ZERO,
    /**
     * A payment from a remembered sender applies on its own only while it is at
     * most this much above what they owe. Anything bigger waits for review: it
     * may be money for something other than FamSub.
     */
    val autoApplyTolerance: BigDecimal = BigDecimal("20.00")
) {
    val configured: Boolean
        get() = token.isNotBlank() && accountId.isNotBlank()
}
