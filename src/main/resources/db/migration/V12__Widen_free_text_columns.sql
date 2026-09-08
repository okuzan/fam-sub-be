-- These columns were declared TEXT by V3 and V5 but exist as varchar(255) in the
-- live database, because Hibernate had already created them. Two of them take
-- free text from a human (`undo_reason`) and one is appended to on every void
-- (`invoice.notes`), so 255 characters is a real overflow, not a theoretical one.
-- Normalise all of them to TEXT so every environment agrees and the entity
-- mappings can pin the type.
ALTER TABLE invoice
    ALTER COLUMN notes TYPE text;

ALTER TABLE admin_action
    ALTER COLUMN summary TYPE text;

ALTER TABLE cost_calculation_batch
    ALTER COLUMN undo_reason TYPE text;

ALTER TABLE invoice_generation_run
    ALTER COLUMN undo_reason TYPE text;
