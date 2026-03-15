package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.Charge
import com.almonium.famsubbe.entity.SubscriptionService
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.YearMonth
import java.util.*

@Repository
interface ChargeRepository : JpaRepository<Charge, UUID> {
    fun findBySubscriptionServiceAndChargeMonth(
        subscriptionService: SubscriptionService, 
        chargeMonth: YearMonth
    ): Charge?
    
    fun findBySubscriptionService(subscriptionService: SubscriptionService): List<Charge>

    fun findByChargeMonth(chargeMonth: YearMonth): List<Charge>
}
