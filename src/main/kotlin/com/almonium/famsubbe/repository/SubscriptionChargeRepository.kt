package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.SubscriptionCharge
import com.almonium.famsubbe.entity.SubscriptionService
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.YearMonth
import java.util.*

@Repository
interface SubscriptionChargeRepository : JpaRepository<SubscriptionCharge, UUID> {
    fun findBySubscriptionServiceAndChargeDate(
        subscriptionService: SubscriptionService, 
        chargeDate: YearMonth
    ): SubscriptionCharge?
    
    fun findBySubscriptionService(subscriptionService: SubscriptionService): List<SubscriptionCharge>
}
