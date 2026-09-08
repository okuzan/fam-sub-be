-- `auto_pay_invoices` never paid anything: it marked a subscriber's invoices PAID
-- on creation without touching their balance. It exists so that family and
-- dependants still take part in cost splits — so everyone else's share stays
-- correct — while never actually being billed. Rename it to say that.
ALTER TABLE subscriber
    RENAME COLUMN auto_pay_invoices TO billing_exempt;
