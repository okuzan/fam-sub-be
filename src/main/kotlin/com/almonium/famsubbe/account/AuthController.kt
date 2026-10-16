package com.almonium.famsubbe.account

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController {

    @GetMapping("/google")
    fun googleLogin(request: HttpServletRequest, response: HttpServletResponse) {
        response.sendRedirect("${request.contextPath}/oauth2/authorization/google")
    }
}
