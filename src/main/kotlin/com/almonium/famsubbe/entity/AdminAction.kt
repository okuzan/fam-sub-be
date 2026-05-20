package com.almonium.famsubbe.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.time.YearMonth
import java.util.*

@Entity
@Table(
    name = "admin_action",
    indexes = [
        Index(name = "idx_admin_action_created_at", columnList = "created_at"),
        Index(name = "idx_admin_action_type", columnList = "action_type"),
        Index(name = "idx_admin_action_target", columnList = "target_type,target_id")
    ]
)
class AdminAction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false)
    var actionType: AdminActionType? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false)
    var targetType: AdminActionTargetType? = null

    @Column(name = "target_id", updatable = false)
    var targetId: UUID? = null

    @Column(name = "subscriber_id", updatable = false)
    var subscriberId: UUID? = null

    @Column(name = "from_month", updatable = false)
    var fromMonth: YearMonth? = null

    @Column(name = "to_month", updatable = false)
    var toMonth: YearMonth? = null

    @Column(name = "summary", nullable = false, updatable = false)
    var summary: String? = null

    @Column(name = "metadata_json", columnDefinition = "TEXT", updatable = false)
    var metadataJson: String? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    var createdByAccountId: UUID? = null
}
