package com.almonium.famsubbe.admin

import tools.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.YearMonth
import java.util.*

@Service
class AdminAuditLogService(
    private val adminActionRepository: AdminActionRepository,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun log(
        createdByAccountId: UUID,
        actionType: AdminActionType,
        targetType: AdminActionTargetType,
        targetId: UUID? = null,
        subscriberId: UUID? = null,
        fromMonth: YearMonth? = null,
        toMonth: YearMonth? = null,
        summary: String,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        adminActionRepository.save(
            AdminAction().apply {
                this.actionType = actionType
                this.targetType = targetType
                this.targetId = targetId
                this.subscriberId = subscriberId
                this.fromMonth = fromMonth
                this.toMonth = toMonth
                this.summary = summary
                this.metadataJson = objectMapper.writeValueAsString(metadata)
                this.createdAt = Instant.now()
                this.createdByAccountId = createdByAccountId
            }
        )
    }
}
