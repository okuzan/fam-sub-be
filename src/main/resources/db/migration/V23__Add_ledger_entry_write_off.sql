-- A ledger entry the admin decided nobody pays, taken off a sent invoice that is
-- then voided and reissued without it. It stays linked to the voided invoice, so
-- it is never back in the uninvoiced pool and its history stays readable.
ALTER TABLE ledger_entry
    ADD COLUMN written_off_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN written_off_reason TEXT;
