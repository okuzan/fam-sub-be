package com.almonium.famsubbe.admin

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.YearMonth
import java.util.*

@RestController
@RequestMapping("/admin/actions")
class AdminActionController(
    private val adminActionService: AdminActionService
) {
    @GetMapping
    fun getActions(
        @RequestParam(required = false) actionType: AdminActionType?,
        @RequestParam(required = false) targetType: AdminActionTargetType?,
        @RequestParam(required = false) targetId: UUID?,
        @RequestParam(required = false) subscriberId: UUID?,
        @RequestParam(required = false) createdByAccountId: UUID?,
        @RequestParam(required = false) dateFrom: Instant?,
        @RequestParam(required = false) dateTo: Instant?,
        @RequestParam(required = false) fromMonth: YearMonth?,
        @RequestParam(required = false) toMonth: YearMonth?,
        @RequestParam(required = false, defaultValue = "50") limit: Int
    ): ResponseEntity<List<AdminActionResponse>> {
        return ResponseEntity.ok(
            adminActionService.getActions(
                AdminActionFilterRequest(
                    actionType = actionType,
                    targetType = targetType,
                    targetId = targetId,
                    subscriberId = subscriberId,
                    createdByAccountId = createdByAccountId,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    fromMonth = fromMonth,
                    toMonth = toMonth,
                    limit = limit
                )
            )
        )
    }

    @PostMapping("/filter")
    fun filterActions(
        @RequestBody filter: AdminActionFilterRequest
    ): ResponseEntity<List<AdminActionResponse>> {
        return ResponseEntity.ok(adminActionService.getActions(filter))
    }

    @GetMapping("/cost-runs")
    fun getCostRuns(
        @RequestParam(required = false, defaultValue = "50") limit: Int
    ): ResponseEntity<List<AdminActionResponse>> {
        return ResponseEntity.ok(adminActionService.getCostRuns(limit))
    }

    @GetMapping("/invoice-runs")
    fun getInvoiceRuns(
        @RequestParam(required = false, defaultValue = "50") limit: Int
    ): ResponseEntity<List<AdminActionResponse>> {
        return ResponseEntity.ok(adminActionService.getInvoiceRuns(limit))
    }
}
