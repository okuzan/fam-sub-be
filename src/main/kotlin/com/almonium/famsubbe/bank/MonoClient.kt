package com.almonium.famsubbe.bank

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant

@Component
class MonoClient(
    private val properties: MonoProperties,
    private val objectMapper: ObjectMapper
) {
    private val restClient = RestClient.builder().baseUrl(properties.apiUrl).build()

    /**
     * Statement items in [from, to], newest first. Mono returns at most
     * [STATEMENT_PAGE_LIMIT] items per call, allows one call a minute per token,
     * and a window of at most 31 days and 1 hour.
     */
    fun statement(from: Instant, to: Instant): List<MonoStatementItem> {
        val body = try {
            restClient.get()
                .uri(
                    "/personal/statement/{account}/{from}/{to}",
                    properties.accountId,
                    from.epochSecond,
                    to.epochSecond
                )
                .header("X-Token", properties.token)
                .retrieve()
                .body(String::class.java)
        } catch (e: HttpClientErrorException.TooManyRequests) {
            throw MonoRateLimitedException()
        }
        return body?.let { objectMapper.readValue<List<MonoStatementItem>>(it) }.orEmpty()
    }

    companion object {
        const val STATEMENT_PAGE_LIMIT = 500
    }
}

class MonoRateLimitedException : RuntimeException(
    "Monobank allows one statement request a minute. Try again shortly."
)

/**
 * [amount] is in kopiyky of the account currency, negative for money leaving
 * the account. [operationAmount] is what the merchant charged, in minor units
 * of [currencyCode] (ISO 4217 numeric). [hold] is true until the payment
 * settles, and the converted [amount] can still change until then.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MonoStatementItem(
    val id: String,
    val time: Long,
    val amount: Long,
    val description: String? = null,
    val comment: String? = null,
    val counterName: String? = null,
    val counterIban: String? = null,
    val operationAmount: Long = amount,
    val currencyCode: Int = UAH_CODE,
    val mcc: Int? = null,
    val hold: Boolean = false
) {
    companion object {
        const val UAH_CODE = 980
    }
}
