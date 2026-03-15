package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.MembershipCreateRequest
import com.almonium.famsubbe.dto.MembershipEndRequest
import com.almonium.famsubbe.dto.MembershipResponse
import com.almonium.famsubbe.dto.MembershipUpdateRequest
import com.almonium.famsubbe.entity.Membership
import com.almonium.famsubbe.repository.MembershipRepository
import com.almonium.famsubbe.repository.SubscriberRepository
import com.almonium.famsubbe.repository.SubscriptionServiceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth
import java.util.*

@Service
@Transactional
class MembershipService(
    private val membershipRepository: MembershipRepository,
    private val subscriberRepository: SubscriberRepository,
    private val subscriptionServiceRepository: SubscriptionServiceRepository
) {

    fun createMembership(request: MembershipCreateRequest): MembershipResponse {
        validateRange(request.startMonth, request.endMonth)

        val subscriber = subscriberRepository.findById(request.subscriberId)
            .orElseThrow { IllegalArgumentException("Subscriber not found: ${request.subscriberId}") }

        val subscriptionService = subscriptionServiceRepository.findById(request.subscriptionServiceId)
            .orElseThrow { IllegalArgumentException("Subscription service not found: ${request.subscriptionServiceId}") }

        ensureNoOverlap(
            subscriptionService.id!!,
            subscriber.id!!,
            request.startMonth,
            request.endMonth,
            null
        )

        val membership = Membership().apply {
            this.subscriptionService = subscriptionService
            this.subscriber = subscriber
            this.startMonth = request.startMonth
            this.endMonth = request.endMonth
        }

        val savedMembership = membershipRepository.saveAndFlush(membership)
        return mapToResponse(savedMembership)
    }

    fun updateMembership(membershipId: UUID, request: MembershipUpdateRequest): MembershipResponse {
        validateRange(request.startMonth, request.endMonth)

        val membership = membershipRepository.findById(membershipId)
            .orElseThrow { IllegalArgumentException("Membership not found: $membershipId") }

        ensureNoOverlap(
            membership.subscriptionService!!.id!!,
            membership.subscriber!!.id!!,
            request.startMonth,
            request.endMonth,
            membership.id
        )

        membership.startMonth = request.startMonth
        membership.endMonth = request.endMonth

        val updatedMembership = membershipRepository.save(membership)
        return mapToResponse(updatedMembership)
    }

    fun endMembership(membershipId: UUID, request: MembershipEndRequest): MembershipResponse {
        val membership = membershipRepository.findById(membershipId)
            .orElseThrow { IllegalArgumentException("Membership not found: $membershipId") }

        if (request.endMonth < membership.startMonth) {
            throw IllegalArgumentException("endMonth cannot be before startMonth")
        }

        membership.endMonth = request.endMonth
        return mapToResponse(membershipRepository.save(membership))
    }

    fun getMembership(membershipId: UUID): MembershipResponse {
        val membership = membershipRepository.findById(membershipId)
            .orElseThrow { IllegalArgumentException("Membership not found: $membershipId") }
        return mapToResponse(membership)
    }

    fun getMembershipsByServiceAndMonth(
        subscriptionServiceId: UUID,
        targetMonth: YearMonth
    ): List<MembershipResponse> {
        return membershipRepository.findActiveByServiceAndMonth(subscriptionServiceId, targetMonth).map { mapToResponse(it) }
    }

    fun getMembershipsBySubscriberAndMonth(subscriberId: UUID, targetMonth: YearMonth): List<MembershipResponse> {
        return membershipRepository.findActiveBySubscriberAndMonth(subscriberId, targetMonth).map { mapToResponse(it) }
    }

    fun getMembershipsByService(subscriptionServiceId: UUID): List<MembershipResponse> {
        val subscriptionService = subscriptionServiceRepository.findById(subscriptionServiceId)
            .orElseThrow { IllegalArgumentException("Subscription service not found: $subscriptionServiceId") }

        return membershipRepository.findBySubscriptionService(subscriptionService).map { mapToResponse(it) }
    }

    fun deleteMembership(membershipId: UUID) {
        if (!membershipRepository.existsById(membershipId)) {
            throw IllegalArgumentException("Membership not found: $membershipId")
        }
        membershipRepository.deleteById(membershipId)
    }

    private fun mapToResponse(membership: Membership): MembershipResponse {
        return MembershipResponse(
            id = membership.id!!,
            subscriptionServiceId = membership.subscriptionService!!.id!!,
            subscriptionServiceName = membership.subscriptionService!!.name ?: "",
            subscriberId = membership.subscriber!!.id!!,
            subscriberName = membership.subscriber!!.name ?: "",
            startMonth = membership.startMonth!!,
            endMonth = membership.endMonth,
            activeNow = isActiveInMonth(membership, YearMonth.now()),
            createdAt = membership.createdAt!!
        )
    }

    private fun validateRange(startMonth: YearMonth, endMonth: YearMonth?) {
        if (endMonth != null && endMonth < startMonth) {
            throw IllegalArgumentException("endMonth cannot be before startMonth")
        }
    }

    /**
     * Ensures that a new or updated membership does not overlap with existing memberships
     * for the same subscriber and subscription service.
     * 
     * @param serviceId The UUID of the subscription service
     * @param subscriberId The UUID of the subscriber
     * @param startMonth The start month of the new/updated membership
     * @param endMonth The end month of the new/updated membership (null for ongoing memberships)
     * @param excludeMembershipId Optional UUID of a membership to exclude from overlap check
     *                           (used when updating an existing membership)
     * 
     * @throws IllegalArgumentException if:
     *         - The subscription service is not found
     *         - The subscriber is not found  
     *         - The new membership range overlaps with an existing membership
     * 
     * Overlap Logic:
     * Two membership ranges [start1, end1] and [start2, end2] overlap if:
     * start1 <= end2 && start2 <= end1
     * 
     * For ongoing memberships (endMonth = null), the end month is treated as YearMonth.of(9999, 12)
     * 
     * Examples:
     * - New: Jan 2024 - Jun 2024 vs Existing: Mar 2024 - Aug 2024 → OVERLAP
     * - New: Jan 2024 - Mar 2024 vs Existing: Apr 2024 - Jun 2024 → NO OVERLAP  
     * - New: Jul 2024 - Dec 2024 vs Existing: Jan 2024 - Jun 2024 → NO OVERLAP
     * - New: Jan 2024 - null (ongoing) vs Existing: Mar 2024 - Aug 2024 → OVERLAP
     */
    private fun ensureNoOverlap(
        serviceId: UUID,
        subscriberId: UUID,
        startMonth: YearMonth,
        endMonth: YearMonth?,
        excludeMembershipId: UUID?
    ) {
        val service = subscriptionServiceRepository.findById(serviceId)
            .orElseThrow { IllegalArgumentException("Subscription service not found: $serviceId") }
        val subscriber = subscriberRepository.findById(subscriberId)
            .orElseThrow { IllegalArgumentException("Subscriber not found: $subscriberId") }

        val existing = membershipRepository.findBySubscriptionServiceAndSubscriber(service, subscriber)
            .filter { it.id != excludeMembershipId }

        val newEnd = endMonth ?: YearMonth.of(9999, 12)
        val overlaps = existing.any { current ->
            val currentEnd = current.endMonth ?: YearMonth.of(9999, 12)
            startMonth <= currentEnd && current.startMonth!! <= newEnd
        }

        if (overlaps) {
            throw IllegalArgumentException("Membership range overlaps with an existing membership")
        }
    }

    private fun isActiveInMonth(membership: Membership, month: YearMonth): Boolean {
        return membership.startMonth!! <= month && (membership.endMonth == null || membership.endMonth!! >= month)
    }
}
