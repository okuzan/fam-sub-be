package com.almonium.famsubbe.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.util.*

@Entity
@Table(name = "account")
class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(nullable = false, unique = true)
    var email: String = ""

    @Column(name = "password", nullable = false)
    var passwordHash: String? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_roles", joinColumns = [JoinColumn(name = "account_id")])
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    var roles: MutableSet<Role> = mutableSetOf()

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Date? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Date? = null

}
