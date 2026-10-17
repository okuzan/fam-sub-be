package com.almonium.famsubbe.bank

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
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
interface BankDebitRepository : JpaRepository<BankDebit, UUID> {
    fun existsByProviderAndExternalId(provider: BankProvider, externalId: String): Boolean

    fun existsByChargeId(chargeId: UUID): Boolean

    fun findByChargeId(chargeId: UUID): List<BankDebit>

    @Query(
        "select d from BankDebit d left join fetch d.subscriptionService left join fetch d.charge " +
            "order by d.occurredAt desc"
    )
    fun findLatest(pageable: Pageable): List<BankDebit>

    @Query(
        "select d from BankDebit d left join fetch d.subscriptionService left join fetch d.charge " +
            "where d.status = :status order by d.occurredAt desc"
    )
    fun findLatestByStatus(status: BankDebitStatus, pageable: Pageable): List<BankDebit>
}

@Repository
interface BankMerchantRuleRepository : JpaRepository<BankMerchantRule, UUID> {
    @Query(
        "select r from BankMerchantRule r left join fetch r.subscriptionService " +
            "order by r.merchant, r.currencyCode, r.amount"
    )
    fun findAllWithService(): List<BankMerchantRule>

    fun findByMerchantAndCurrencyCodeAndAmount(merchant: String, currencyCode: Int, amount: BigDecimal): BankMerchantRule?
}

@Repository
interface BankSyncStateRepository : JpaRepository<BankSyncState, String>
