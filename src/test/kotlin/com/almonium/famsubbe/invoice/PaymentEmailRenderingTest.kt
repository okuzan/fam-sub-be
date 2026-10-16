package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.config.AppEmailProperties
import com.almonium.famsubbe.config.PaymentProperties
import com.almonium.famsubbe.config.ZeptoMailProperties
import com.almonium.famsubbe.payment.PaymentDetailsService
import com.almonium.famsubbe.subscriber.Subscriber
import com.almonium.famsubbe.subscriber.SubscriberRepository
import com.almonium.famsubbe.subscriber.SubscriberStandingService
import com.almonium.famsubbe.util.HtmlFileWriter
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the real templates: a broken expression would otherwise only show up when a mail is sent. */
class PaymentEmailRenderingTest {
    private val rendered = mutableListOf<String>()
    private val htmlFileWriter = object : HtmlFileWriter() {
        override fun saveHtmlToFile(htmlContent: String, prefix: String) {
            rendered += htmlContent
        }
    }
    private val paymentDetailsService = PaymentDetailsService(
        PaymentProperties(
            recipientName = "Кузан Олег",
            iban = "UA583220010000026205363007788",
            cardNumber = "4441111068471360"
        ),
        mock(SubscriberRepository::class.java),
        mock(SubscriberStandingService::class.java),
        "https://famsub.example"
    )
    private val emailService = DefaultInvoiceEmailService(
        SpringTemplateEngine().apply {
            setTemplateResolver(ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                characterEncoding = "UTF-8"
            })
        },
        htmlFileWriter,
        ZeptoMailProperties("https://mail.example", "key", "noreply@example.com", "FamSub"),
        AppEmailProperties(dryRun = true),
        paymentDetailsService,
        "https://famsub.example"
    )

    private val subscriber = Subscriber().apply {
        id = UUID.randomUUID()
        name = "Поліна"
        email = "polina@example.com"
        balance = BigDecimal.ZERO
        payToken = "token123"
        paymentCode = "7K2Q9X"
    }

    @Test
    fun `an unpaid invoice email links to the pay page and shows the purpose`() {
        val sent = emailService.sendInvoiceEmail(invoice(InvoiceStatus.SENT), emptyList(), BigDecimal("31.93"))

        assertTrue(sent)
        val html = rendered.single()
        assertContains(html, "https://famsub.example/pay/token123")
        assertContains(html, "FamSub FS-7K2Q9X")
        assertContains(html, "UA583220010000026205363007788")
        assertFalse("Privat" in html)
    }

    @Test
    fun `a paid invoice email asks for nothing`() {
        emailService.sendInvoiceEmail(invoice(InvoiceStatus.PAID), emptyList(), BigDecimal.ZERO)

        assertFalse("/pay/" in rendered.single())
    }

    @Test
    fun `situation emails link to the pay page`() {
        val payment = paymentDetailsService.forSubscriber(subscriber, BigDecimal("31.93"))

        emailService.sendSituationEmail("polina@example.com", "Поліна", BigDecimal("31.93"), BigDecimal.ZERO, emptyList(), emptyList(), payment)
        emailService.sendWeeklySituationEmail("polina@example.com", "Поліна", BigDecimal("31.93"), BigDecimal.ZERO, emptyList(), emptyList(), payment)

        assertTrue(rendered.size == 2 && rendered.all { "https://famsub.example/pay/token123" in it })
    }

    private fun invoice(status: InvoiceStatus) = Invoice().apply {
        id = UUID.randomUUID()
        subscriber = this@PaymentEmailRenderingTest.subscriber
        fromMonth = YearMonth.of(2026, 9)
        toMonth = YearMonth.of(2026, 9)
        invoiceDate = LocalDate.of(2026, 9, 22)
        totalAmount = BigDecimal("31.93")
        this.status = status
        createdAt = Instant.now()
        statusChangedAt = Instant.now()
        createdByAccountId = UUID.randomUUID()
    }
}
