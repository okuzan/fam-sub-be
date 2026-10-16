-- Incoming bank transfers pulled from the Monobank personal API. Only credits
-- that came from another person are stored: own-card top-ups, refunds and the
-- like never reach this table. One row per bank operation; the unique key is
-- what makes a payment apply exactly once, even when two app instances poll
-- at the same time during a blue/green switch.
CREATE TABLE bank_transaction (
    id UUID PRIMARY KEY,
    provider VARCHAR(16) NOT NULL,
    external_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    description TEXT,
    comment TEXT,
    counter_name VARCHAR(255),
    counter_iban VARCHAR(34),
    sender_key VARCHAR(320),
    sender_name VARCHAR(255),
    status VARCHAR(16) NOT NULL,
    subscriber_id UUID REFERENCES subscriber (id),
    match_method VARCHAR(16),
    review_note TEXT,
    settled_invoice_count INTEGER NOT NULL DEFAULT 0,
    settled_amount NUMERIC(19, 2),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by_account_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_bank_transaction_provider_external UNIQUE (provider, external_id),
    CONSTRAINT ck_bank_transaction_status CHECK (status IN ('UNMATCHED', 'APPLIED', 'IGNORED'))
);

CREATE INDEX idx_bank_transaction_status ON bank_transaction (status);
CREATE INDEX idx_bank_transaction_occurred_at ON bank_transaction (occurred_at);
CREATE INDEX idx_bank_transaction_subscriber ON bank_transaction (subscriber_id);

-- Who a sender is. Learned when an admin assigns a payment and keeps
-- "remember this sender" on; later payments from the same key apply on their
-- own. An ignored sender's payments are filed as IGNORED without review.
-- created_by_account_id is NULL when the system learned the sender itself.
CREATE TABLE bank_sender (
    id UUID PRIMARY KEY,
    sender_key VARCHAR(320) NOT NULL,
    sender_name VARCHAR(255),
    subscriber_id UUID REFERENCES subscriber (id) ON DELETE CASCADE,
    ignored BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_account_id UUID,
    CONSTRAINT uk_bank_sender_key UNIQUE (sender_key),
    CONSTRAINT ck_bank_sender_target CHECK (ignored OR subscriber_id IS NOT NULL)
);

-- How far the statement of each bank account has been read.
CREATE TABLE bank_sync_state (
    account_id VARCHAR(64) PRIMARY KEY,
    synced_to TIMESTAMP WITH TIME ZONE NOT NULL,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT
);
