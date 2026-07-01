package com.almonium.famsubbe.subscription

import com.almonium.famsubbe.subscriber.Subscriber
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.YearMonth
import java.util.*

@Repository
interface MembershipRepository : JpaRepository<Membership, UUID> {
    @Query(
        """
        select m
        from Membership m
        where m.subscriptionService.id = :serviceId
          and m.startMonth <= :targetMonth
          and (m.endMonth is null or m.endMonth >= :targetMonth)
        """
    )
    fun findActiveByServiceAndMonth(serviceId: UUID, targetMonth: YearMonth): List<Membership>

    @Query(
        """
        select m
        from Membership m
        where m.subscriber.id = :subscriberId
          and m.startMonth <= :targetMonth
          and (m.endMonth is null or m.endMonth >= :targetMonth)
        """
    )
    fun findActiveBySubscriberAndMonth(subscriberId: UUID, targetMonth: YearMonth): List<Membership>

    fun findBySubscriptionServiceAndSubscriber(subscriptionService: SubscriptionService, subscriber: Subscriber): List<Membership>

    fun findBySubscriber(subscriber: Subscriber): List<Membership>

    fun findBySubscriptionService(subscriptionService: SubscriptionService): List<Membership>
}
