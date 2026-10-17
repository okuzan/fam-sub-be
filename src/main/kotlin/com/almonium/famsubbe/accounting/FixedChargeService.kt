package com.almonium.famsubbe.accounting

import com.almonium.famsubbe.subscription.SubscriptionService
import com.almonium.famsubbe.subscription.SubscriptionServiceRepository
import com.almonium.famsubbe.util.BillingTime
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.YearMonth

/**
 * Charges every service marked [com.almonium.famsubbe.subscription.SubscriptionService.fixedMonthlyCharge]
 * its price for the month, once. Runs daily rather than only on the 1st, so a
 * deploy or a restart on that day cannot skip a month; a month that already has
 * a charge for the service, typed in or not, is left alone.
 */
@Service
class FixedChargeService(
    private val subscriptionServiceRepository: SubscriptionServiceRepository,
    private val chargeRepository: ChargeRepository
) {
    private val log = LoggerFactory.getLogger(FixedChargeService::class.java)

    @Scheduled(cron = "0 0 6 * * *", zone = BillingTime.ZONE_ID)
    fun chargeOnSchedule() {
        try {
            createCharges(BillingTime.currentMonth())
        } catch (e: Exception) {
            log.warn("Fixed monthly charges failed", e)
        }
    }

    /** The charges created; each in its own transaction, so one failure leaves the others. */
    fun createCharges(month: YearMonth): List<Charge> =
        subscriptionServiceRepository.findByFixedMonthlyChargeTrue().mapNotNull { service ->
            if (service.currency != SubscriptionService.UAH) return@mapNotNull null
            if (chargeRepository.findBySubscriptionServiceAndChargeMonth(service, month) != null) {
                return@mapNotNull null
            }
            try {
                chargeRepository.saveAndFlush(Charge().apply {
                    subscriptionService = service
                    amount = service.price
                    chargeMonth = month
                    description = "Fixed monthly charge"
                }).also { log.info("Created fixed {} charge of {} for {}", service.name, service.price, month) }
            } catch (e: DataIntegrityViolationException) {
                // The other blue/green instance created it first.
                null
            }
        }
}
