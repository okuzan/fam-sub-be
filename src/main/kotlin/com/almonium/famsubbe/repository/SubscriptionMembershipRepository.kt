package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.Account
import com.almonium.famsubbe.entity.SubscriptionMembership
import com.almonium.famsubbe.entity.SubscriptionService
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.YearMonth
import java.util.*

@Repository
interface SubscriptionMembershipRepository : JpaRepository<SubscriptionMembership, UUID> {
    fun findBySubscriptionServiceAndAccountAndMembershipMonth(
        subscriptionService: SubscriptionService,
        account: Account,
        membershipMonth: YearMonth
    ): SubscriptionMembership?

    fun findBySubscriptionServiceAndMembershipMonth(
        subscriptionService: SubscriptionService,
        membershipMonth: YearMonth
    ): List<SubscriptionMembership>

    fun findBySubscriptionService(subscriptionService: SubscriptionService): List<SubscriptionMembership>

    fun findByAccount(account: Account): List<SubscriptionMembership>
}
