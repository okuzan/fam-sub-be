package com.almonium.famsubbe.invoice

import com.almonium.famsubbe.account.AdminInviteEmailService
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
    private val templateEngine = SpringTemplateEngine().apply {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            characterEncoding = "UTF-8"
        })
    }
    private val zepto = ZeptoMailProperties("https://mail.example", "key", "noreply@example.com", "FamSub")
    private val emailService = DefaultInvoiceEmailService(
        templateEngine,
        htmlFileWriter,
        zepto,
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

    @Test
    fun `an all-clear situation email asks for nothing`() {
        val payment = paymentDetailsService.forSubscriber(subscriber, BigDecimal.ZERO)

        emailService.sendSituationEmail("polina@example.com", "Поліна", BigDecimal("-35.00"), BigDecimal("35.00"), emptyList(), emptyList(), payment)

        val html = rendered.single()
        assertContains(html, "You’re all paid up, Поліна")
        assertFalse("/pay/" in html)
    }

    @Test
    fun `the debt paid email and the admin invite render`() {
        emailService.sendDebtPaidEmail("polina@example.com", "Поліна", 3, BigDecimal("2008.00"), BigDecimal.ZERO, BigDecimal.ZERO)
        AdminInviteEmailService(htmlFileWriter, zepto, AppEmailProperties(dryRun = true), templateEngine, "https://famsub.example")
            .sendInvite("friend@example.com", "https://famsub.example/admin-invites/accept?token=abc")

        assertContains(rendered[0], "All 3 of your open invoices are now marked paid.")
        assertContains(rendered[1], "https://famsub.example/admin-invites/accept?token=abc")
        assertTrue(rendered.none { "cid:" in it }, "a dry run embeds the logo instead of referencing an attachment")
    }

    @Test
    fun `payment received reads right whether it paid everything, some, or nothing yet`() {
        val payment = paymentDetailsService.forSubscriber(subscriber, BigDecimal("372.00"))
        val aug = EmailInvoiceLine("Aug 2026", "Netflix Premium, Spotify Family", BigDecimal("186.00"))
        val sepOct = EmailInvoiceLine("Sep–Oct 2026", "Netflix Premium, Spotify Family · sent 1 Nov", BigDecimal("372.00"))
        fun received(amount: String, settled: List<EmailInvoiceLine>, open: List<EmailInvoiceLine>, balance: String, toPay: String) =
            PaymentReceivedEmail(
                "polina@example.com", "Поліна", BigDecimal(amount), Instant.parse("2026-11-06T12:32:00Z"), "CHERNYSHOVA POLINA",
                settled, open, BigDecimal.ZERO, BigDecimal(balance), BigDecimal(toPay), payment
            )

        emailService.sendPaymentReceivedEmail(received("558.00", listOf(aug, sepOct), emptyList(), "0", "0"))
        emailService.sendPaymentReceivedEmail(received("186.00", listOf(aug), listOf(sepOct), "0", "372.00"))
        emailService.sendPaymentReceivedEmail(received("100.00", emptyList(), listOf(aug, sepOct), "100.00", "458.00"))

        assertContains(rendered[0], "both of your open invoices, so there’s nothing left to pay")
        assertFalse("/pay/" in rendered[0])
        assertContains(rendered[1], "Sep–Oct 2026 is still open.")
        assertContains(rendered[1], "https://famsub.example/pay/token123")
        assertContains(rendered[2], "It will settle your Aug 2026 invoice once the other 86,00\u00a0₴ arrives.")
        assertContains(rendered[2], "Held as credit")
    }

    @Test
    fun `a cancelled invoice email shows the reason and the new total`() {
        val voided = invoice(InvoiceStatus.VOID).apply { notes = "Voided: The Netflix price was wrong" }

        emailService.sendInvoiceCancelledEmail(voided, BigDecimal("146.00"))

        val html = rendered.single()
        assertContains(html, "your Sep 2026 invoice is cancelled. Please don’t pay it.")
        assertContains(html, "The Netflix price was wrong")
        assertContains(html, "https://famsub.example/pay/token123")
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
