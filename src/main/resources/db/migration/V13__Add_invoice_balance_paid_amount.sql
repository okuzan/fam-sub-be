-- How much of an invoice was settled by drawing down the subscriber's prepaid
-- balance. Moving a PAID invoice back to DRAFT or SENT refunds exactly this
-- amount and clears it. NULL means the invoice was not paid from balance
-- (cash, debt pay-off, or billing-exempt settlement), so reopening it refunds
-- nothing.
ALTER TABLE invoice
    ADD COLUMN balance_paid_amount NUMERIC(19, 2);

-- Backfill from the audit log: an invoice that is PAID right now, and whose
-- most recent status-changing action was a pay-from-balance, single or bulk,
-- was paid from balance. The bulk action records its invoices in
-- metadata_json.paidInvoiceIds.
WITH status_events AS (
    SELECT target_id AS invoice_id, created_at, action_type
    FROM admin_action
    WHERE target_type = 'INVOICE'
      AND target_id IS NOT NULL
      AND action_type IN (
          'INVOICE_PAID_FROM_BALANCE',
          'INVOICE_MARKED_PAID',
          'INVOICE_STATUS_UPDATED',
          'INVOICE_VOIDED'
      )
    UNION ALL
    SELECT CAST(paid.invoice_id AS UUID), a.created_at, a.action_type
    FROM admin_action a
    CROSS JOIN LATERAL jsonb_array_elements_text(
        CAST(a.metadata_json AS JSONB) -> 'paidInvoiceIds'
    ) AS paid(invoice_id)
    WHERE a.action_type = 'INVOICE_BULK_PAID_FROM_BALANCE'
      AND a.metadata_json IS NOT NULL
),
latest AS (
    SELECT DISTINCT ON (invoice_id) invoice_id, action_type
    FROM status_events
    ORDER BY invoice_id, created_at DESC
)
UPDATE invoice i
SET balance_paid_amount = i.total_amount
FROM latest
WHERE latest.invoice_id = i.id
  AND i.status = 'PAID'
  AND latest.action_type IN ('INVOICE_PAID_FROM_BALANCE', 'INVOICE_BULK_PAID_FROM_BALANCE');
