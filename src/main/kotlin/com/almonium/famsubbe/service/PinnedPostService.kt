package com.almonium.famsubbe.service

import com.almonium.famsubbe.config.PaymentProperties
import com.almonium.famsubbe.entity.InvoiceStatus
import com.almonium.famsubbe.entity.Subscriber
import com.almonium.famsubbe.repository.InvoiceRepository
import com.almonium.famsubbe.repository.SubscriberRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PinnedPostService(
    private val subscriberRepository: SubscriberRepository,
    private val invoiceRepository: InvoiceRepository,
    private val paymentProperties: PaymentProperties
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PinnedPostService::class.java)
        const val PINNED_POST_TEMPLATE = "/templates/pinned.md"
        const val TIMESTAMP_PATTERN = "MMM dd HH:mm"
        const val LINE = "————————————————————————\n"
    }

    @Value("\${users.excluded:}")
    private val excludedUsersString: String = ""

    fun generatePinnedPost(): String {
        logger.info("Starting pinned post generation")
        
        logger.info("Fetching all subscribers")
        val users = subscriberRepository.findAllByOrderByName()
        logger.info("Found {} subscribers", users.size)
        
        val excludedUsers = excludedUsersString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        logger.info("Excluded users: {}", excludedUsers)
        
        logger.info("Filtering users and calculating balances")
        val filteredUsers = users.filter { user -> 
            excludedUsers.none { excluded -> user.name?.equals(excluded, ignoreCase = true) == true }
        }.map { user ->
            logger.debug("Processing user: {}", user.name)
            val unpaidInvoices = invoiceRepository.findBySubscriberAndStatusNot(user, InvoiceStatus.PAID)
            logger.debug("Found {} unpaid invoices for user {}", unpaidInvoices.size, user.name)
            val totalOwed = unpaidInvoices.sumOf { it.totalAmount ?: BigDecimal.ZERO }
            val accountBalance = (user.balance ?: BigDecimal.ZERO) - totalOwed
            logger.debug("User {} balance: {}, total owed: {}, account balance: {}", user.name, user.balance, totalOwed, accountBalance)
            Pair(user, accountBalance)
        }.sortedWith(compareBy<Pair<Subscriber, BigDecimal>> { it.second }.thenBy { it.first.name })
        
        logger.info("Filtered to {} users with calculated balances", filteredUsers.size)

        val currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN))
        logger.info("Current date formatted: {}", currentDate)
        
        logger.info("Reading template from classpath: {}", PINNED_POST_TEMPLATE)
        val templateStream = this::class.java.getResourceAsStream(PINNED_POST_TEMPLATE)
            ?: throw IllegalStateException("Cannot find template resource at: $PINNED_POST_TEMPLATE")
        var template = templateStream.bufferedReader().use { it.readText() }
        logger.info("Template read successfully, length: {}", template.length)
        
        // Replace payment method placeholders with a clean map-based approach
        logger.info("Replacing payment placeholders")
        val replacements = mapOf(
            "{{MONO_NAME}}" to (paymentProperties.methods["mono"]?.name ?: ""),
            "{{MONO_CURRENCY}}" to (paymentProperties.methods["mono"]?.currency ?: ""),
            "{{MONO_NUMBER}}" to (paymentProperties.methods["mono"]?.number ?: ""),
            "{{PRIVAT_NAME}}" to (paymentProperties.methods["privat"]?.name ?: ""),
            "{{PRIVAT_CURRENCY}}" to (paymentProperties.methods["privat"]?.currency ?: ""),
            "{{PRIVAT_NUMBER}}" to (paymentProperties.methods["privat"]?.number ?: "")
        )
        
        logger.debug("Payment replacements: {}", replacements)
        
        replacements.forEach { (placeholder, value) ->
            template = template.replace(placeholder, value)
        }
        logger.info("Payment placeholders replaced successfully")
        
        val negativeBalances = StringBuilder()
        val zeroBalances = StringBuilder()
        val positiveBalances = StringBuilder()

        logger.info("Categorizing users by balance type")
        for ((user, accountBalance) in filteredUsers) {
            val balanceEmoji: String
            val currentBalance: StringBuilder
            val displayAmount: BigDecimal
            
            when {
                accountBalance < BigDecimal.ZERO -> {
                    balanceEmoji = "➖"
                    currentBalance = negativeBalances
                    displayAmount = accountBalance.abs()
                    logger.debug("User {} has negative balance: {}", user.name, accountBalance)
                }
                accountBalance.compareTo(BigDecimal.ZERO) == 0 -> {
                    balanceEmoji = "0️⃣"
                    currentBalance = zeroBalances
                    displayAmount = BigDecimal.ZERO
                    logger.debug("User {} has zero balance", user.name)
                }
                else -> {
                    balanceEmoji = "➕"
                    currentBalance = positiveBalances
                    displayAmount = accountBalance
                    logger.debug("User {} has positive balance: {}", user.name, accountBalance)
                }
            }
            
            currentBalance.append(balanceEmoji).append(" ")
            
            if (displayAmount.compareTo(BigDecimal.ZERO) != 0) {
                val roundedAmount = displayAmount.setScale(0, BigDecimal.ROUND_HALF_UP)
                currentBalance.append("**").append(roundedAmount).append("** ")
            }
            
            currentBalance.append("**").append(user.name).append("**")
                .append("\n")
        }

        val balances = StringBuilder()
        if (negativeBalances.isNotEmpty()) {
            balances.append(negativeBalances)
            logger.info("Added {} negative balance users", negativeBalances.toString().split("\n").size - 1)
        }
        if (zeroBalances.isNotEmpty()) {
            balances.append(LINE).append(zeroBalances)
            logger.info("Added {} zero balance users", zeroBalances.toString().split("\n").size - 1)
        }
        if (positiveBalances.isNotEmpty()) {
            balances.append(LINE).append(positiveBalances)
            logger.info("Added {} positive balance users", positiveBalances.toString().split("\n").size - 1)
        }

        logger.info("Formatting final template")
        val result = String.format(
            template,
            currentDate,
            balances.toString()
        )
        
        logger.info("Successfully generated pinned post with total length: {}", result.length)
        return result
    }
}
