package com.almonium.famsubbe.payment

import com.almonium.famsubbe.config.PaymentProperties
import com.almonium.famsubbe.subscriber.PaymentCode
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import com.almonium.famsubbe.subscriber.SubscriberStandingService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/** How one subscriber pays: the shared recipient, their own purpose, and their pay page. */
@Service
class PaymentDetailsService(
    private val paymentProperties: PaymentProperties,
    private val subscriberRepository: SubscriberRepository,
    private val standingService: SubscriberStandingService,
    @Value($$"${app.web-domain}") private val webDomain: String
) {
    fun forSubscriber(subscriber: Subscriber, amount: BigDecimal?): PaymentDetails {
        val purpose = PaymentCode.purposeFor(requireNotNull(subscriber.paymentCode))
        val qrAmount = amount?.takeIf { it > BigDecimal.ZERO }
        return PaymentDetails(
            recipientName = paymentProperties.recipientName,
            iban = paymentProperties.iban,
            cardNumber = paymentProperties.cardNumber,
            purpose = purpose,
            payUrl = payUrl(requireNotNull(subscriber.payToken)),
            nbuQrUrl = paymentProperties.recipientTaxId.takeIf { it.isNotBlank() }?.let { taxId ->
                NbuQrCode.link(
                    recipientName = paymentProperties.recipientName,
                    iban = paymentProperties.iban,
                    recipientCode = taxId,
                    purpose = purpose,
                    amount = qrAmount
                )
            }
        )
    }

    @Transactional(readOnly = true)
    fun getPayPage(payToken: String): PayPageResponse {
        val subscriber = subscriberRepository.findByPayToken(payToken)
            ?: throw NoSuchElementException("This payment link is not valid")
        val unpaidInvoices = standingService.unpaidInvoices(subscriber)
        val owed = SubscriberStandingService.amountOwed(unpaidInvoices, subscriber)
        val amountDue = owed.max(BigDecimal.ZERO)
        val details = forSubscriber(subscriber, amountDue)

        return PayPageResponse(
            subscriberName = subscriber.name.orEmpty(),
            amountDue = amountDue,
            credit = owed.negate().max(BigDecimal.ZERO),
            unpaidInvoiceCount = unpaidInvoices.size,
            recipientName = details.recipientName,
            iban = details.iban,
            cardNumber = details.cardNumber,
            purpose = details.purpose,
            nbuQrUrl = details.nbuQrUrl
        )
    }

    fun payUrl(payToken: String): String = "$webDomain/pay/$payToken"
}

data class PaymentDetails(
    val recipientName: String,
    val iban: String,
    val cardNumber: String,
    /** Must reach the bank unchanged: its payment code is how the transfer is recognised. */
    val purpose: String,
    val payUrl: String,
    /** Null until app.payment.recipient-tax-id is set. */
    val nbuQrUrl: String?
)

data class PayPageResponse(
    val subscriberName: String,
    /** Zero when nothing is owed. */
    val amountDue: BigDecimal,
    /** Prepaid credit left over after every unpaid invoice. */
    val credit: BigDecimal,
    val unpaidInvoiceCount: Int,
    val recipientName: String,
    val iban: String,
    val cardNumber: String,
    val purpose: String,
    /** Carries amountDue when it is above zero; null until the recipient tax number is configured. */
    val nbuQrUrl: String?
)
