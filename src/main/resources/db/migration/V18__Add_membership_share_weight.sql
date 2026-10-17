-- How big a member's share of the service is, relative to the other members
-- that month: weights 2, 2, 1 and 1 split a bill 2/6, 2/6, 1/6 and 1/6.
ALTER TABLE membership ADD COLUMN share_weight INTEGER NOT NULL DEFAULT 1;
ALTER TABLE membership ADD CONSTRAINT ck_membership_share_weight CHECK (share_weight >= 1);

-- The weights a ledger entry was split by, frozen at calculation time so a later
-- membership edit cannot rewrite how an old bill was shared. Every entry before
-- this was an even split.
ALTER TABLE ledger_entry ADD COLUMN share_weight INTEGER NOT NULL DEFAULT 1;
ALTER TABLE ledger_entry ADD COLUMN total_weight INTEGER;
UPDATE ledger_entry SET total_weight = participant_count;
ALTER TABLE ledger_entry ALTER COLUMN total_weight SET NOT NULL;
