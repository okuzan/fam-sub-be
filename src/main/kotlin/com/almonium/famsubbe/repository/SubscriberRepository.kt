package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.Subscriber
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SubscriberRepository : JpaRepository<Subscriber, UUID> {
    fun findByNameIgnoreCaseStartingWith(namePrefix: String): List<Subscriber>
    fun findAllByOrderByName(): List<Subscriber>
}
