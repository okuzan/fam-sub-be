package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.CostCalculationResult
import com.almonium.famsubbe.service.AccountService
import com.almonium.famsubbe.service.CostCalculationService
import com.almonium.famsubbe.util.AuthenticationUtil
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

@RestController
@RequestMapping("/admin/cost-calculations")
class AdminCostCalculationController(
    private val costCalculationService: CostCalculationService,
    private val accountService: AccountService
) {
    @PostMapping("/{yearMonth}")
    fun calculate(
        @PathVariable yearMonth: YearMonth,
        authentication: Authentication
    ): ResponseEntity<CostCalculationResult> {
        val performedByAccountId = AuthenticationUtil.resolveAccountId(authentication, accountService)
        val result = costCalculationService.calculateAndRecordCosts(yearMonth, performedByAccountId)
        return ResponseEntity.ok(result)
    }
}
