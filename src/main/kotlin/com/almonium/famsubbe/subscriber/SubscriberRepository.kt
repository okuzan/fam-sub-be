package com.almonium.famsubbe.subscriber

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SubscriberRepository : JpaRepository<Subscriber, UUID> {
    fun findByNameIgnoreCaseStartingWith(namePrefix: String): List<Subscriber>
    fun findAllByOrderByName(): List<Subscriber>
    fun findByEmailIgnoreCase(email: String): Subscriber?
}
