package com.almonium.famsubbe.config

import com.almonium.famsubbe.dto.ApiErrorResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

/**
 * CSRF defence for a cookie session.
 *
 * Every *.almonium.com site is same-site with the API, so SameSite=Lax does not
 * stop one of them from POSTing to it with the admin's session cookie. Requiring
 * a custom header does: a browser only sends one cross-origin after a CORS
 * preflight, and CORS admits only app.web-domain. HTML forms cannot set headers
 * at all. The web client sends it on every call through apiFetch.
 */
class RequestedWithHeaderFilter(
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.method in SAFE_METHODS || !request.getHeader(HEADER).isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                ApiErrorResponse(
                    message = "Missing $HEADER header",
                    status = HttpStatus.FORBIDDEN.value(),
                    path = request.requestURI
                )
            )
        )
    }

    companion object {
        const val HEADER = "X-Requested-With"
        private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS", "TRACE")
    }
}
