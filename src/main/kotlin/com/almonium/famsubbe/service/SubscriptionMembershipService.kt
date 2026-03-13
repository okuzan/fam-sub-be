package com.almonium.famsubbe.service

import com.almonium.famsubbe.dto.SubscriptionMembershipCreateRequest
import com.almonium.famsubbe.dto.SubscriptionMembershipResponse
import com.almonium.famsubbe.dto.SubscriptionMembershipUpdateRequest
import com.almonium.famsubbe.entity.SubscriptionMembership
import com.almonium.famsubbe.repository.AccountRepository
import com.almonium.famsubbe.repository.SubscriptionMembershipRepository
import com.almonium.famsubbe.repository.SubscriptionServiceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth
import java.util.*

@Service
@Transactional
class SubscriptionMembershipService(
    private val membershipRepository: SubscriptionMembershipRepository,
    private val accountRepository: AccountRepository,
    private val subscriptionServiceRepository: SubscriptionServiceRepository
) {

    fun createMembership(request: SubscriptionMembershipCreateRequest): SubscriptionMembershipResponse {
        val account = accountRepository.findById(request.accountId)
            .orElseThrow { IllegalArgumentException("Account not found: ${request.accountId}") }

        val subscriptionService = subscriptionServiceRepository.findById(request.subscriptionServiceId)
            .orElseThrow { IllegalArgumentException("Subscription service not found: ${request.subscriptionServiceId}") }

        // Check if membership already exists for this service/account/month combination
        val existingMembership = membershipRepository.findBySubscriptionServiceAndAccountAndMembershipMonth(
            subscriptionService, account, request.membershipMonth
        )
        if (existingMembership != null) {
            throw IllegalArgumentException(
                "Membership already exists for account ${account.email} in ${subscriptionService.name} for ${request.membershipMonth}"
            )
        }

        val membership = SubscriptionMembership().apply {
            this.subscriptionService = subscriptionService
            this.account = account
            this.membershipMonth = request.membershipMonth
            this.shareWeight = request.shareWeight
        }

        val savedMembership = membershipRepository.save(membership)
        return mapToResponse(savedMembership)
    }

    fun updateMembership(membershipId: UUID, request: SubscriptionMembershipUpdateRequest): SubscriptionMembershipResponse {
        val membership = membershipRepository.findById(membershipId)
            .orElseThrow { IllegalArgumentException("Membership not found: $membershipId") }

        membership.shareWeight = request.shareWeight

        val updatedMembership = membershipRepository.save(membership)
        return mapToResponse(updatedMembership)
    }

    fun getMembership(membershipId: UUID): SubscriptionMembershipResponse {
        val membership = membershipRepository.findById(membershipId)
            .orElseThrow { IllegalArgumentException("Membership not found: $membershipId") }
        return mapToResponse(membership)
    }

    fun getMembershipsByServiceAndMonth(
        subscriptionServiceId: UUID,
        membershipMonth: YearMonth
    ): List<SubscriptionMembershipResponse> {
        val subscriptionService = subscriptionServiceRepository.findById(subscriptionServiceId)
            .orElseThrow { IllegalArgumentException("Subscription service not found: $subscriptionServiceId") }

        return membershipRepository.findBySubscriptionServiceAndMembershipMonth(subscriptionService, membershipMonth)
            .map { mapToResponse(it) }
    }

    fun getMembershipsByAccount(accountId: UUID): List<SubscriptionMembershipResponse> {
        val account = accountRepository.findById(accountId)
            .orElseThrow { IllegalArgumentException("Account not found: $accountId") }

        return membershipRepository.findByAccount(account).map { mapToResponse(it) }
    }

    fun getMembershipsByService(subscriptionServiceId: UUID): List<SubscriptionMembershipResponse> {
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

    private fun mapToResponse(membership: SubscriptionMembership): SubscriptionMembershipResponse {
        return SubscriptionMembershipResponse(
            id = membership.id!!,
            subscriptionServiceId = membership.subscriptionService!!.id!!,
            subscriptionServiceName = membership.subscriptionService!!.name ?: "",
            accountId = membership.account!!.id!!,
            accountEmail = membership.account!!.email,
            membershipMonth = membership.membershipMonth!!,
            shareWeight = membership.shareWeight,
            createdAt = membership.createdAt!!,
            updatedAt = membership.updatedAt!!
        )
    }
}
