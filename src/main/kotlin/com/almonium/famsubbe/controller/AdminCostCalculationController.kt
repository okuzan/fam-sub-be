package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.CostCalculationBatchResponse
import com.almonium.famsubbe.dto.CostCalculationRequest
import com.almonium.famsubbe.dto.CostCalculationResult
import com.almonium.famsubbe.dto.CostCalculationSuggestion
import com.almonium.famsubbe.entity.toResponse
import com.almonium.famsubbe.service.AccountService
import com.almonium.famsubbe.service.CostCalculationService
import com.almonium.famsubbe.util.AuthenticationUtil
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/cost-calculations")
class AdminCostCalculationController(
    private val costCalculationService: CostCalculationService,
    private val accountService: AccountService
) {
    @PostMapping
    fun calculate(
        @RequestBody request: CostCalculationRequest,
        authentication: Authentication
    ): ResponseEntity<CostCalculationResult> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val result = costCalculationService.calculateAndRecordCosts(
            request.fromMonth,
            request.toMonth,
            performedByAccountId
        )
        return ResponseEntity.ok(result)
    }

    @GetMapping("/suggested-period")
    fun getSuggestedPeriod(): ResponseEntity<CostCalculationSuggestion> {
        val suggestion = costCalculationService.getSuggestedCalculationPeriod()
        return ResponseEntity.ok(suggestion)
    }

    @GetMapping
    fun getCostCalculations(): ResponseEntity<List<CostCalculationBatchResponse>> {
        val batches = costCalculationService.getRecentCalculationBatches()
        return ResponseEntity.ok(batches.map { it.toResponse() })
    }
}
