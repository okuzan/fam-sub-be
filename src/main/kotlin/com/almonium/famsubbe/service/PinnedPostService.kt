package com.almonium.famsubbe.service

import com.almonium.famsubbe.config.PaymentProperties
import com.almonium.famsubbe.entity.InvoiceStatus
import com.almonium.famsubbe.entity.Subscriber
import com.almonium.famsubbe.repository.InvoiceRepository
import com.almonium.famsubbe.repository.SubscriberRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PinnedPostService(
    private val subscriberRepository: SubscriberRepository,
    private val invoiceRepository: InvoiceRepository,
    private val paymentProperties: PaymentProperties
) {
    companion object {
        const val PINNED_POST_TEMPLATE = "src/main/resources/templates/pinned.md"
        const val TIMESTAMP_PATTERN = "MMM dd HH:mm"
        const val LINE = "————————————————————————\n"
    }

    @Value("\${users.excluded:}")
    private val excludedUsersString: String = ""

    fun generatePinnedPost(): String {
        val users = subscriberRepository.findAllByOrderByName()
        
        val excludedUsers = excludedUsersString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val filteredUsers = users.filter { user -> 
            excludedUsers.none { excluded -> user.name?.equals(excluded, ignoreCase = true) == true }
        }.map { user ->
            val unpaidInvoices = invoiceRepository.findBySubscriberAndStatusNot(user, InvoiceStatus.PAID)
            val totalOwed = unpaidInvoices.sumOf { it.totalAmount ?: BigDecimal.ZERO }
            val accountBalance = (user.balance ?: BigDecimal.ZERO) - totalOwed
            Pair(user, accountBalance)
        }.sortedWith(compareBy<Pair<Subscriber, BigDecimal>> { it.second }.thenBy { it.first.name })

        val currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN))
        
        // Read template
        var template = Files.readString(Paths.get(PINNED_POST_TEMPLATE))
        
        // Replace payment method placeholders with a clean map-based approach
        val replacements = mapOf(
            "{{MONO_NAME}}" to (paymentProperties.methods["mono"]?.name ?: ""),
            "{{MONO_CURRENCY}}" to (paymentProperties.methods["mono"]?.currency ?: ""),
            "{{MONO_NUMBER}}" to (paymentProperties.methods["mono"]?.number ?: ""),
            "{{PRIVAT_NAME}}" to (paymentProperties.methods["privat"]?.name ?: ""),
            "{{PRIVAT_CURRENCY}}" to (paymentProperties.methods["privat"]?.currency ?: ""),
            "{{PRIVAT_NUMBER}}" to (paymentProperties.methods["privat"]?.number ?: "")
        )
        
        replacements.forEach { (placeholder, value) ->
            template = template.replace(placeholder, value)
        }
        
        val negativeBalances = StringBuilder()
        val zeroBalances = StringBuilder()
        val positiveBalances = StringBuilder()

        for ((user, accountBalance) in filteredUsers) {
            val balanceEmoji: String
            val currentBalance: StringBuilder
            val displayAmount: BigDecimal
            
            when {
                accountBalance < BigDecimal.ZERO -> {
                    balanceEmoji = "➖"
                    currentBalance = negativeBalances
                    displayAmount = accountBalance.abs()
                }
                accountBalance.compareTo(BigDecimal.ZERO) == 0 -> {
                    balanceEmoji = "0️⃣"
                    currentBalance = zeroBalances
                    displayAmount = BigDecimal.ZERO
                }
                else -> {
                    balanceEmoji = "➕"
                    currentBalance = positiveBalances
                    displayAmount = accountBalance
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
        }
        if (zeroBalances.isNotEmpty()) {
            balances.append(LINE).append(zeroBalances)
        }
        if (positiveBalances.isNotEmpty()) {
            balances.append(LINE).append(positiveBalances)
        }

        return String.format(
            template,
            currentDate,
            balances.toString()
        )
    }
}
