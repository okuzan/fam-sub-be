package com.almonium.famsubbe.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController {

    @GetMapping("/status")
    fun authStatus(): ResponseEntity<Void> {
        val auth = SecurityContextHolder.getContext().authentication
        return if (auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
    }

    @GetMapping("/google")
    fun googleLogin(request: HttpServletRequest, response: HttpServletResponse) {
        response.sendRedirect("${request.contextPath}/oauth2/authorization/google")
    }
}
