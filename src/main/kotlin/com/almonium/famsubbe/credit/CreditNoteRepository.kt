package com.almonium.famsubbe.credit

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface CreditNoteRepository : JpaRepository<CreditNote, UUID> {
    @Query("select c from CreditNote c join fetch c.subscriber order by c.occurredOn desc, c.createdAt desc")
    fun findAllWithSubscriber(): List<CreditNote>

    @Query(
        "select c from CreditNote c join fetch c.subscriber s " +
            "where s.id = :subscriberId order by c.occurredOn desc, c.createdAt desc"
    )
    fun findBySubscriberIdWithSubscriber(subscriberId: UUID): List<CreditNote>
}
