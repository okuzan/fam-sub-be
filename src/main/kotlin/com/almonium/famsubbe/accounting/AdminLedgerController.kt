package com.almonium.famsubbe.accounting

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth
import java.util.*

@RestController
@RequestMapping("/admin/ledger")
class AdminLedgerController(
    private val ledgerService: LedgerService
) {
    @GetMapping
    fun filter(
        @RequestParam(required = false) id: UUID?,
        @RequestParam(required = false) chargeId: UUID?,
        @RequestParam(required = false) serviceId: UUID?,
        @RequestParam(required = false) subscriptionServiceId: UUID?,
        @RequestParam(required = false) subscriberId: UUID?,
        @RequestParam(required = false) recordedMonth: YearMonth?,
        @RequestParam(required = false) fromMonth: YearMonth?,
        @RequestParam(required = false) toMonth: YearMonth?,
        @RequestParam(required = false) calculationBatchId: UUID?,
        @RequestParam(required = false) generatedByAccountId: UUID?,
        @RequestParam(required = false) invoiceId: UUID?
    ): ResponseEntity<List<LedgerEntryResponse>> {
        return ResponseEntity.ok(
            ledgerService.filter(
                LedgerEntryFilterRequest(
                    id = id,
                    chargeId = chargeId,
                    serviceId = serviceId,
                    subscriptionServiceId = subscriptionServiceId,
                    subscriberId = subscriberId,
                    recordedMonth = recordedMonth,
                    fromMonth = fromMonth,
                    toMonth = toMonth,
                    calculationBatchId = calculationBatchId,
                    generatedByAccountId = generatedByAccountId,
                    invoiceId = invoiceId
                )
            )
        )
    }

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

    @GetMapping("/calculation-batches/latest")
    fun latestCalculationBatch(): ResponseEntity<LedgerCalculationBatchResponse> {
        return ledgerService.getLatestCalculationBatch()
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/calculation-batches/{calculationBatchId}")
    fun calculationBatch(
        @PathVariable calculationBatchId: UUID
    ): ResponseEntity<LedgerCalculationBatchResponse> {
        return ResponseEntity.ok(ledgerService.getCalculationBatch(calculationBatchId))
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

    @GetMapping("/month/{yearMonth}")
    fun byMonth(@PathVariable yearMonth: YearMonth): ResponseEntity<List<LedgerEntryResponse>> {
        return ResponseEntity.ok(ledgerService.getByMonth(yearMonth))
    }

    @GetMapping("/subscriber/{subscriberId}")
    fun bySubscriber(@PathVariable subscriberId: UUID): ResponseEntity<List<LedgerEntryResponse>> {
        return ResponseEntity.ok(ledgerService.getBySubscriber(subscriberId))
    }
}
