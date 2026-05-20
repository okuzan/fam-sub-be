ALTER TABLE invoice
    DROP CONSTRAINT IF EXISTS invoice_status_check;

ALTER TABLE invoice
    ADD CONSTRAINT invoice_status_check
        CHECK (status IN ('DRAFT', 'SENT', 'PAID', 'VOID'));
