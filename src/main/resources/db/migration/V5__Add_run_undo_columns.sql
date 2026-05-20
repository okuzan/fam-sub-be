ALTER TABLE cost_calculation_batch
    ADD COLUMN undone_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN undone_by_account_id UUID,
    ADD COLUMN undo_reason TEXT;

ALTER TABLE invoice_generation_run
    ADD COLUMN undone_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN undone_by_account_id UUID,
    ADD COLUMN undo_reason TEXT;
