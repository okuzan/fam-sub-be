package com.almonium.famsubbe.config

import com.almonium.famsubbe.entity.Role
import com.almonium.famsubbe.security.AccountOidcUserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Value("\${app.web-domain}") private val webDomain: String
) {

    companion object {
        val PUBLIC_URL_PATTERNS = arrayOf(
            // Swagger
            "/swagger-ui/**",
            "/v3/api-docs/**",
            // OAuth2
            "/oauth2/authorization/**",
            "/login/oauth2/**",
            // Auth endpoints
            "/auth/**",
            // Public endpoints
            "/public/**",
            // Actuator
            "/actuator/health/**",
            "/actuator/info"
        )
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf(webDomain)
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    @Bean
    fun filterChain(
        http: HttpSecurity,
        accountOidcUserService: AccountOidcUserService
    ): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .authorizeHttpRequests { authz ->
                authz
                    .requestMatchers("/admin/**").hasRole(Role.ADMIN.name)
                    .requestMatchers("/subscriber/**").hasRole(Role.SUBSCRIBER.name)
                    .requestMatchers(*PUBLIC_URL_PATTERNS).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2.userInfoEndpoint { userInfo ->
                    userInfo.oidcUserService(accountOidcUserService)
                }
                oauth2.defaultSuccessUrl(webDomain, true)
                oauth2.failureUrl("$webDomain/login?error=true")
            }
            .sessionManagement { sessions ->
                sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            }
            .httpBasic { }
        return http.build()
    }
}
