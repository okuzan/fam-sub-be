package com.almonium.famsubbe.controller

import com.almonium.famsubbe.dto.LedgerEntryResponse
import com.almonium.famsubbe.service.LedgerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth
import java.util.*

@RestController
@RequestMapping("/admin/ledger")
class AdminLedgerController(
    private val ledgerService: LedgerService
) {
    @GetMapping("/month/{yearMonth}")
    fun byMonth(@PathVariable yearMonth: YearMonth): ResponseEntity<List<LedgerEntryResponse>> {
        return ResponseEntity.ok(ledgerService.getByMonth(yearMonth))
    }

    @GetMapping("/subscriber/{subscriberId}")
    fun bySubscriber(@PathVariable subscriberId: UUID): ResponseEntity<List<LedgerEntryResponse>> {
        return ResponseEntity.ok(ledgerService.getBySubscriber(subscriberId))
    }
}
