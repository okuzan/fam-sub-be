-- Baseline of the schema as it stood before V1.
--
-- Every table below was originally created implicitly by Hibernate's
-- `ddl-auto: update`, which is why no migration ever created them. That left the
-- schema unreproducible from source: V1..V10 only patch a structure that nothing
-- in this repository defines. This migration captures that starting point so a
-- fresh database can be built from migrations alone, and so `ddl-auto: validate`
-- has something to validate against.
--
-- Shapes here are transcribed from the live database, including Hibernate's
-- defaults (varchar(255) for String, numeric(38,2) for an unqualified
-- BigDecimal). Do not "tidy" them: on existing databases every statement below
-- is a no-op and the real columns keep these types.
--
-- Runs out of order on databases that already have V1..V10 applied, hence
-- `spring.flyway.out-of-order: true`. Every statement is guarded, so applying it
-- to an already-populated schema changes nothing.

CREATE TABLE IF NOT EXISTS account (
    id         uuid                        NOT NULL,
    created_at timestamp(6)                NOT NULL,
    email      character varying(255)      NOT NULL,
    password   character varying(255)      NOT NULL,
    updated_at timestamp(6)                NOT NULL,
    CONSTRAINT account_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS account_roles (
    account_id uuid                   NOT NULL,
    role       character varying(255),
    CONSTRAINT account_roles_role_check CHECK (role IN ('ADMIN', 'SUBSCRIBER'))
);

CREATE TABLE IF NOT EXISTS subscriber (
    id         uuid                     NOT NULL,
    created_at timestamp(6)             NOT NULL,
    email      character varying(255)   NOT NULL,
    name       character varying(255)   NOT NULL,
    updated_at timestamp(6)             NOT NULL,
    balance    numeric(38, 2) DEFAULT 0 NOT NULL,
    CONSTRAINT subscriber_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS subscription_service (
    id         uuid                   NOT NULL,
    created_at timestamp(6)           NOT NULL,
    name       character varying(255) NOT NULL,
    price      numeric(38, 2)         NOT NULL,
    updated_at timestamp(6)           NOT NULL,
    CONSTRAINT subscription_service_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS membership (
    id                      uuid                   NOT NULL,
    created_at              timestamp(6) with time zone NOT NULL,
    start_month             character varying(255) NOT NULL,
    end_month               character varying(255),
    subscriber_id           uuid                   NOT NULL,
    subscription_service_id uuid                   NOT NULL,
    CONSTRAINT membership_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS charge (
    id                      uuid                        NOT NULL,
    amount                  numeric(19, 2)              NOT NULL,
    charge_month            character varying(255)      NOT NULL,
    created_at              timestamp(6)                NOT NULL,
    description             character varying(255),
    subscription_service_id uuid                        NOT NULL,
    CONSTRAINT charge_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS cost_calculation_batch (
    id                    uuid                             NOT NULL,
    created_at            timestamp(6) with time zone      NOT NULL,
    created_by_account_id uuid                             NOT NULL,
    from_month            character varying(255)           NOT NULL,
    to_month              character varying(255)           NOT NULL,
    CONSTRAINT cost_calculation_batch_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS invoice (
    id                    uuid                        NOT NULL,
    created_at            timestamp(6) with time zone NOT NULL,
    created_by_account_id uuid                        NOT NULL,
    email_sent            boolean                     NOT NULL,
    from_month            character varying(255)      NOT NULL,
    to_month              character varying(255)      NOT NULL,
    notes                 character varying(255),
    sent_at               timestamp(6) with time zone,
    status                character varying(255)      NOT NULL,
    total_amount          numeric(19, 2)              NOT NULL,
    subscriber_id         uuid                        NOT NULL,
    CONSTRAINT invoice_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ledger_entry (
    id                      uuid                        NOT NULL,
    amount                  numeric(19, 2)              NOT NULL,
    calculated_at           timestamp(6) with time zone NOT NULL,
    calculation_batch_id    uuid                        NOT NULL,
    notes                   character varying(255),
    participant_count       integer                     NOT NULL,
    recorded_month          character varying(255)      NOT NULL,
    charge_id               uuid                        NOT NULL,
    subscriber_id           uuid                        NOT NULL,
    subscription_service_id uuid                        NOT NULL,
    invoice_id              uuid,
    CONSTRAINT ledger_entry_pkey PRIMARY KEY (id)
);

-- Unique constraints. Named exactly as the live database has them: the
-- Hibernate-generated names are stable hashes of the column list, so recreating
-- them under nicer names would make a fresh database diverge from production.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ukq0uja26qgu1atulenwup9rxyr') THEN
        ALTER TABLE account ADD CONSTRAINT ukq0uja26qgu1atulenwup9rxyr UNIQUE (email);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ukcedvgb58oehnp1wb4u2g9w6s') THEN
        ALTER TABLE account_roles ADD CONSTRAINT ukcedvgb58oehnp1wb4u2g9w6s UNIQUE (account_id, role);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ukewxd6mo4yfd3pmkcfoaec1sun') THEN
        ALTER TABLE subscriber ADD CONSTRAINT ukewxd6mo4yfd3pmkcfoaec1sun UNIQUE (email);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_charge_service_month') THEN
        ALTER TABLE charge ADD CONSTRAINT uk_charge_service_month UNIQUE (subscription_service_id, charge_month);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_ledger_charge_subscriber') THEN
        ALTER TABLE ledger_entry ADD CONSTRAINT uk_ledger_charge_subscriber UNIQUE (charge_id, subscriber_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fktp61eta5i06bug3w1qr6286uf') THEN
        ALTER TABLE account_roles ADD CONSTRAINT fktp61eta5i06bug3w1qr6286uf
            FOREIGN KEY (account_id) REFERENCES account (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkekd52uoowx2xlrysby114fjod') THEN
        ALTER TABLE membership ADD CONSTRAINT fkekd52uoowx2xlrysby114fjod
            FOREIGN KEY (subscriber_id) REFERENCES subscriber (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fksbhmqff4867yh187slcrowk8v') THEN
        ALTER TABLE membership ADD CONSTRAINT fksbhmqff4867yh187slcrowk8v
            FOREIGN KEY (subscription_service_id) REFERENCES subscription_service (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkdvtg2eajqjj00dfaahs7r4jp8') THEN
        ALTER TABLE charge ADD CONSTRAINT fkdvtg2eajqjj00dfaahs7r4jp8
            FOREIGN KEY (subscription_service_id) REFERENCES subscription_service (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkla7khidn0uesufhch2679ckjp') THEN
        ALTER TABLE cost_calculation_batch ADD CONSTRAINT fkla7khidn0uesufhch2679ckjp
            FOREIGN KEY (created_by_account_id) REFERENCES account (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkp6fqxj0cndtl7tuj03uag2mce') THEN
        ALTER TABLE invoice ADD CONSTRAINT fkp6fqxj0cndtl7tuj03uag2mce
            FOREIGN KEY (subscriber_id) REFERENCES subscriber (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk1wt821rhwk1ghhy3jx7vebgrt') THEN
        ALTER TABLE ledger_entry ADD CONSTRAINT fk1wt821rhwk1ghhy3jx7vebgrt
            FOREIGN KEY (calculation_batch_id) REFERENCES cost_calculation_batch (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkiut7596tjfirwnfnty6geka3q') THEN
        ALTER TABLE ledger_entry ADD CONSTRAINT fkiut7596tjfirwnfnty6geka3q
            FOREIGN KEY (charge_id) REFERENCES charge (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkqhcn4iugyy2xtdee168on8lgl') THEN
        ALTER TABLE ledger_entry ADD CONSTRAINT fkqhcn4iugyy2xtdee168on8lgl
            FOREIGN KEY (subscriber_id) REFERENCES subscriber (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkbrvmk70hs9mnnu08wung4756') THEN
        ALTER TABLE ledger_entry ADD CONSTRAINT fkbrvmk70hs9mnnu08wung4756
            FOREIGN KEY (subscription_service_id) REFERENCES subscription_service (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkdbpscefmxdgh2cinc8sil1ujq') THEN
        ALTER TABLE ledger_entry ADD CONSTRAINT fkdbpscefmxdgh2cinc8sil1ujq
            FOREIGN KEY (invoice_id) REFERENCES invoice (id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_membership_subscriber ON membership (subscriber_id);
CREATE INDEX IF NOT EXISTS idx_membership_service ON membership (subscription_service_id);
CREATE INDEX IF NOT EXISTS idx_membership_start_month ON membership (start_month);
CREATE INDEX IF NOT EXISTS idx_membership_end_month ON membership (end_month);

CREATE INDEX IF NOT EXISTS idx_charge_month ON charge (charge_month);

CREATE INDEX IF NOT EXISTS idx_invoice_subscriber ON invoice (subscriber_id);
CREATE INDEX IF NOT EXISTS idx_invoice_status ON invoice (status);
CREATE INDEX IF NOT EXISTS idx_invoice_from_month ON invoice (from_month);
CREATE INDEX IF NOT EXISTS idx_invoice_to_month ON invoice (to_month);
CREATE INDEX IF NOT EXISTS idx_invoice_created_at ON invoice (created_at);

CREATE INDEX IF NOT EXISTS idx_ledger_subscriber ON ledger_entry (subscriber_id);
CREATE INDEX IF NOT EXISTS idx_ledger_charge ON ledger_entry (charge_id);
CREATE INDEX IF NOT EXISTS idx_ledger_recorded_month ON ledger_entry (recorded_month);
CREATE INDEX IF NOT EXISTS idx_ledger_calculation_batch ON ledger_entry (calculation_batch_id);
CREATE INDEX IF NOT EXISTS idx_ledger_invoice ON ledger_entry (invoice_id);
