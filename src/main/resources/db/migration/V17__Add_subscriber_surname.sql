-- The surname as the bank writes it. A transfer whose sender name holds both
-- the subscriber's name and this surname applies to them on its own.
ALTER TABLE subscriber ADD COLUMN surname VARCHAR(255);
