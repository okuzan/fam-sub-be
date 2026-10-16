package com.almonium.famsubbe.bank

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface BankTransactionRepository : JpaRepository<BankTransaction, UUID> {
    fun existsByProviderAndExternalId(provider: BankProvider, externalId: String): Boolean

    fun findAllByOrderByOccurredAtDesc(pageable: Pageable): List<BankTransaction>

    fun findByStatusOrderByOccurredAtDesc(status: BankTransactionStatus, pageable: Pageable): List<BankTransaction>

    fun findBySubscriberIdAndStatusOrderByOccurredAtDesc(
        subscriberId: UUID,
        status: BankTransactionStatus,
        pageable: Pageable
    ): List<BankTransaction>
}

@Repository
interface BankSenderRepository : JpaRepository<BankSender, UUID> {
    fun findBySenderKey(senderKey: String): BankSender?

    @Query("select s from BankSender s left join fetch s.subscriber order by s.senderName")
    fun findAllWithSubscriber(): List<BankSender>
}

@Repository
interface BankSyncStateRepository : JpaRepository<BankSyncState, String>
