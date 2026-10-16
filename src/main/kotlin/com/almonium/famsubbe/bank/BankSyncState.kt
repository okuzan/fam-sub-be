package com.almonium.famsubbe.bank

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** How far a bank account's statement has been read. */
@Entity
@Table(name = "bank_sync_state")
class BankSyncState {
    @Id
    @Column(name = "account_id")
    var accountId: String? = null

    @Column(name = "synced_to", nullable = false)
    var syncedTo: Instant? = null

    @Column(name = "last_attempt_at")
    var lastAttemptAt: Instant? = null

    @Column(name = "last_success_at")
    var lastSuccessAt: Instant? = null

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null
}
