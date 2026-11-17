-- A credit note is an invoice the other way round: a subscriber paid something
-- for the group (or for the admin) and is owed it back. It credits their
-- balance like an incoming payment, but records why and when, which a bare
-- balance edit never did. Undone rather than deleted, so the history stays.
CREATE TABLE credit_note (
    id UUID PRIMARY KEY,
    subscriber_id UUID NOT NULL REFERENCES subscriber (id),
    amount NUMERIC(19, 2) NOT NULL,
    occurred_on DATE NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_account_id UUID NOT NULL,
    settled_invoice_count INTEGER NOT NULL DEFAULT 0,
    settled_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    email_sent_at TIMESTAMP WITH TIME ZONE,
    undone_at TIMESTAMP WITH TIME ZONE,
    undone_by_account_id UUID,
    CONSTRAINT ck_credit_note_amount CHECK (amount > 0),
    CONSTRAINT ck_credit_note_reason CHECK (BTRIM(reason) <> '')
);

CREATE INDEX idx_credit_note_subscriber ON credit_note (subscriber_id);

-- The credit note whose money settled an invoice, like paid_by_bank_transaction_id.
-- Cleared when the invoice is reopened.
ALTER TABLE invoice
    ADD COLUMN paid_by_credit_note_id UUID REFERENCES credit_note (id);

CREATE INDEX idx_invoice_paid_by_credit_note ON invoice (paid_by_credit_note_id);
