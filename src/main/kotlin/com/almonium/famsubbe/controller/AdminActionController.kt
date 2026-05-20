package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.AdminActionResponse
import com.almonium.famsubbe.service.AdminActionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/actions")
class AdminActionController(
    private val adminActionService: AdminActionService
) {
    @GetMapping
    fun getActions(
        @RequestParam(required = false, defaultValue = "50") limit: Int
    ): ResponseEntity<List<AdminActionResponse>> {
        return ResponseEntity.ok(adminActionService.getActions(limit))
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
