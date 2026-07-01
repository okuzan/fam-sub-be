package com.almonium.famsubbe.finance

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/finance")
class AdminFinanceController(
    private val financeSummaryService: FinanceSummaryService
) {
    @GetMapping("/summary")
    fun getSummary(): ResponseEntity<FinanceSummaryResponse> =
        ResponseEntity.ok(financeSummaryService.getSummary())
}
