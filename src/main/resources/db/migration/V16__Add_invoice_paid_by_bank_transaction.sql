-- The bank transfer that settled an invoice, when one did. Lets the invoice view
-- say "paid by bank transfer from ..." and the cabinet list which invoices a
-- payment covered. Cleared when the invoice is reopened.
ALTER TABLE invoice
    ADD COLUMN paid_by_bank_transaction_id UUID REFERENCES bank_transaction (id);

CREATE INDEX idx_invoice_paid_by_bank_transaction ON invoice (paid_by_bank_transaction_id);
