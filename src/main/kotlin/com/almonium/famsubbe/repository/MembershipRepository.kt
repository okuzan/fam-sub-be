package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.Subscriber
import com.almonium.famsubbe.entity.Membership
import com.almonium.famsubbe.entity.SubscriptionService
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.YearMonth
import java.util.*

@Repository
interface MembershipRepository : JpaRepository<Membership, UUID> {
    fun findBySubscriptionServiceAndSubscriberAndMembershipMonth(
        subscriptionService: SubscriptionService,
        subscriber: Subscriber,
        membershipMonth: YearMonth
    ): Membership?

    fun findBySubscriptionServiceAndMembershipMonth(
        subscriptionService: SubscriptionService,
        membershipMonth: YearMonth
    ): List<Membership>

    fun findBySubscriptionService(subscriptionService: SubscriptionService): List<Membership>

    fun findBySubscriber(subscriber: Subscriber): List<Membership>
}
