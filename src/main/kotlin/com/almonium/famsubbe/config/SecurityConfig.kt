package com.almonium.famsubbe.config

import com.almonium.famsubbe.entity.Role
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    companion object {
        val PUBLIC_URL_PATTERNS = arrayOf(
            // Swagger
            "/swagger-ui/**",
            "/v3/api-docs/**",
            // OAuth2
            "/oauth2/authorization/**",
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
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { authz ->
                authz
                    .requestMatchers("/admin/**").hasRole(Role.ADMIN.name)
                    .requestMatchers("/subscriber/**").hasRole(Role.SUBSCRIBER.name)
                    .requestMatchers(*PUBLIC_URL_PATTERNS).permitAll()
                    .anyRequest().authenticated()
            }
            .httpBasic { }
        return http.build()
    }
}
