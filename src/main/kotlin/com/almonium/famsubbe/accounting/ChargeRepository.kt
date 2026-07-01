package com.almonium.famsubbe.accounting

import com.almonium.famsubbe.subscription.SubscriptionService
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.YearMonth
import java.util.*

@Repository
interface ChargeRepository : JpaRepository<Charge, UUID> {
    fun findBySubscriptionServiceAndChargeMonth(
        subscriptionService: SubscriptionService, 
        chargeMonth: YearMonth
    ): Charge?
    
    fun findBySubscriptionServiceOrderByChargeMonthDescCreatedAtDescIdDesc(
        subscriptionService: SubscriptionService,
        pageable: Pageable
    ): Page<Charge>

    fun findByChargeMonth(chargeMonth: YearMonth): List<Charge>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select charge
        from Charge charge
        where charge.chargeMonth = :chargeMonth
        order by charge.id
    """)
    fun findByChargeMonthForUpdate(chargeMonth: YearMonth): List<Charge>
}
