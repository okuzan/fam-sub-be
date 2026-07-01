ALTER TABLE invoice
    ADD COLUMN invoice_date DATE;

UPDATE invoice
SET invoice_date = (created_at AT TIME ZONE 'Europe/Kyiv')::date
WHERE invoice_date IS NULL;

ALTER TABLE invoice
    ALTER COLUMN invoice_date SET NOT NULL;

CREATE INDEX idx_invoice_invoice_date
    ON invoice (invoice_date);
