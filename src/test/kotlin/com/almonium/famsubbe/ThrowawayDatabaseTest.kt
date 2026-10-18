package com.almonium.famsubbe

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * A Spring test against its own Postgres container, started by Testcontainers
 * (Docker must be running), never the database in `.env`. That one may be a
 * tunnel to prod, and these tests write: services, subscribers, charges,
 * invoices. [ServiceConnection] outranks `.env` and environment variables.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(properties = ["app.email.dry-run=true"])
@Import(ThrowawayDatabase::class)
annotation class ThrowawayDatabaseTest

@TestConfiguration(proxyBeanMethods = false)
class ThrowawayDatabase {
    @Bean
    @ServiceConnection
    fun postgres() = PostgreSQLContainer("postgres:16")
}
