CREATE TABLE invoice_generation_run (
    id UUID PRIMARY KEY,
    from_month VARCHAR(7) NOT NULL,
    to_month VARCHAR(7) NOT NULL,
    subscriber_id UUID,
    send_email BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_account_id UUID NOT NULL
);

ALTER TABLE invoice
    ADD COLUMN invoice_generation_run_id UUID;

ALTER TABLE invoice
    ADD CONSTRAINT fk_invoice_generation_run
        FOREIGN KEY (invoice_generation_run_id)
        REFERENCES invoice_generation_run (id);

CREATE INDEX idx_invoice_generation_run
    ON invoice (invoice_generation_run_id);
