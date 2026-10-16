package com.almonium.famsubbe.config

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestedWithHeaderFilterTest {
    private val filter = RequestedWithHeaderFilter(JsonMapper.builder().build())

    @Test
    fun `a write without the header is refused`() {
        val (response, chain) = run(MockHttpServletRequest("POST", "/admin/invoices/drafts/email"))

        assertEquals(403, response.status)
        assertTrue("X-Requested-With" in response.contentAsString)
        assertNull(chain.request)
    }

    @Test
    fun `a write with the header passes`() {
        val request = MockHttpServletRequest("DELETE", "/admin/subscribers/1").apply {
            addHeader("X-Requested-With", "XMLHttpRequest")
        }

        val (response, chain) = run(request)

        assertEquals(200, response.status)
        assertNotNull(chain.request)
    }

    @Test
    fun `reads and preflights pass without the header`() {
        listOf("GET", "OPTIONS").forEach { method ->
            val (_, chain) = run(MockHttpServletRequest(method, "/admin/invoices"))
            assertNotNull(chain.request, method)
        }
    }

    private fun run(request: MockHttpServletRequest): Pair<MockHttpServletResponse, MockFilterChain> {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        return response to chain
    }
}
