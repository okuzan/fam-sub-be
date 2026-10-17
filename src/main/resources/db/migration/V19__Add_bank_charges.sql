-- Charges read from the card instead of typed in. Every subscription is paid
-- from the Monobank card that app.bank.mono reads, so each bill shows up in the
-- statement as a debit with the merchant's name and the exact amount the
-- merchant charged in its own currency ($100.00, €9.99), before conversion.

-- What a bill looks like: the bank's merchant name contains [merchant], and
-- the merchant charged exactly [amount] in [currency_code] (ISO 4217 numeric:
-- 840 USD, 978 EUR, 980 UAH). A matching debit becomes that month's charge for
-- the service, or is filed as ignored when the rule says so (a personal plan
-- paid from the same card). A debit from a known merchant with no matching
-- amount waits for review: a price change, a proration, a new plan.
CREATE TABLE bank_merchant_rule (
    id UUID PRIMARY KEY,
    merchant VARCHAR(255) NOT NULL,
    currency_code INTEGER NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    subscription_service_id UUID REFERENCES subscription_service (id) ON DELETE CASCADE,
    ignored BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_account_id UUID,
    CONSTRAINT uk_bank_merchant_rule UNIQUE (merchant, currency_code, amount),
    CONSTRAINT ck_bank_merchant_rule_target CHECK (ignored OR subscription_service_id IS NOT NULL),
    CONSTRAINT ck_bank_merchant_rule_merchant CHECK (merchant = LOWER(merchant) AND merchant <> '')
);

-- One card payment to a known merchant, as read from the statement. Debits to
-- merchants no rule mentions (groceries, utilities) are never stored. One row
-- per bank operation, so a bill is charged exactly once even when both
-- blue/green instances poll at the same time.
CREATE TABLE bank_debit (
    id UUID PRIMARY KEY,
    provider VARCHAR(16) NOT NULL,
    external_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- What left the card, in UAH, positive.
    amount NUMERIC(19, 2) NOT NULL,
    -- What the merchant charged, in its own currency, positive.
    original_amount NUMERIC(19, 2) NOT NULL,
    currency_code INTEGER NOT NULL,
    description TEXT,
    mcc INTEGER,
    -- The rule merchant it was recognised by, lowercased.
    merchant VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    subscription_service_id UUID REFERENCES subscription_service (id) ON DELETE SET NULL,
    charge_id UUID REFERENCES charge (id) ON DELETE SET NULL,
    charge_month VARCHAR(7),
    match_method VARCHAR(16),
    review_note TEXT,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by_account_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_bank_debit_provider_external UNIQUE (provider, external_id),
    CONSTRAINT ck_bank_debit_status CHECK (status IN ('UNMATCHED', 'CHARGED', 'IGNORED'))
);

CREATE INDEX idx_bank_debit_status ON bank_debit (status);
CREATE INDEX idx_bank_debit_occurred_at ON bank_debit (occurred_at);
CREATE INDEX idx_bank_debit_charge ON bank_debit (charge_id);

-- A service whose bill is fixed in UAH and not worth matching (YouTube comes
-- from Google, which also bills for everything else): its price is charged on
-- its own at the start of every month.
ALTER TABLE subscription_service ADD COLUMN fixed_monthly_charge BOOLEAN NOT NULL DEFAULT FALSE;

-- The plans paid from the card as of October 2026. A no-op where a service
-- of that name does not exist.
INSERT INTO bank_merchant_rule (id, merchant, currency_code, amount, subscription_service_id, ignored, created_at)
SELECT gen_random_uuid(), rule.merchant, rule.currency_code, rule.amount, service.id, FALSE, NOW()
FROM (VALUES ('ChatGPT', 'openai', 840, 100.00),
             ('Claude', 'claude', 840, 200.00),
             ('Netflix', 'netflix', 978, 9.99),
             ('Spotify', 'spotify', 840, 7.99)) AS rule (service_name, merchant, currency_code, amount)
JOIN subscription_service service ON service.name = rule.service_name;

-- Three YouTube family plans at ₴299.
UPDATE subscription_service SET fixed_monthly_charge = TRUE, price = 897.00 WHERE name = 'YouTube';
