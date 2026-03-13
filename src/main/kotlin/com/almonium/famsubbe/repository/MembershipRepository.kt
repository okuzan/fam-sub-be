package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.Account
import com.almonium.famsubbe.entity.Membership
import com.almonium.famsubbe.entity.SubscriptionService
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.YearMonth
import java.util.*

@Repository
interface MembershipRepository : JpaRepository<Membership, UUID> {
    fun findBySubscriptionServiceAndAccountAndMembershipMonth(
        subscriptionService: SubscriptionService,
        account: Account,
        membershipMonth: YearMonth
    ): Membership?

    fun findBySubscriptionServiceAndMembershipMonth(
        subscriptionService: SubscriptionService,
        membershipMonth: YearMonth
    ): List<Membership>

    fun findBySubscriptionService(subscriptionService: SubscriptionService): List<Membership>

    fun findByAccount(account: Account): List<Membership>
}
