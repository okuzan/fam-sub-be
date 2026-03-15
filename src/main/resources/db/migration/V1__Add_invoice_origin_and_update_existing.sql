-- Add origin column to invoice table
ALTER TABLE invoice ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'SUBSCRIPTION_LEDGER';
