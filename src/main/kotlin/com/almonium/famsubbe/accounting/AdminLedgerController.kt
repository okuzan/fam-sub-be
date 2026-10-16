package com.almonium.famsubbe.accounting

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/admin/ledger")
class AdminLedgerController(
    private val ledgerService: LedgerService
) {
    @PostMapping("/filter")
    fun filterByBody(@RequestBody filter: LedgerEntryFilterRequest): ResponseEntity<List<LedgerEntryResponse>> {
        return ResponseEntity.ok(ledgerService.filter(filter))
    }

    @GetMapping("/calculation-batches")
    fun calculationBatches(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<LedgerCalculationBatchResponse>> {
        return ResponseEntity.ok(ledgerService.getRecentCalculationBatches(limit))
    }

    @GetMapping("/calculation-batches/{calculationBatchId}/entries")
    fun byCalculationBatch(
        @PathVariable calculationBatchId: UUID
    ): ResponseEntity<List<LedgerEntryResponse>> {
        return ResponseEntity.ok(ledgerService.getByCalculationBatch(calculationBatchId))
    }

    @GetMapping("/entries/{ledgerEntryId}")
    fun byId(@PathVariable ledgerEntryId: UUID): ResponseEntity<LedgerEntryResponse> {
        return ResponseEntity.ok(ledgerService.getById(ledgerEntryId))
    }
}
