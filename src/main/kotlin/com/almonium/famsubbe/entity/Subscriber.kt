package com.almonium.famsubbe.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "subscriber")
class Subscriber {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(nullable = false)
    var name: String? = null

    @Column(nullable = false, unique = true)
    var email: String? = null

}
