ALTER TABLE invoice
    ADD COLUMN status_changed_at TIMESTAMP WITH TIME ZONE;

UPDATE invoice
SET status_changed_at = created_at
WHERE status_changed_at IS NULL;

ALTER TABLE invoice
    ALTER COLUMN status_changed_at SET NOT NULL;

CREATE INDEX idx_invoice_status_changed_at
    ON invoice (status_changed_at);
