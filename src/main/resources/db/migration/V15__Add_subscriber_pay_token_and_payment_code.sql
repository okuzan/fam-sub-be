-- pay_token: the unguessable part of a subscriber's public pay page link
-- (/pay/<token>), sent in their emails so paying needs no login.
-- payment_code: the short code put in the payment purpose ("FamSub FS-XXXXXX"),
-- which lets an incoming transfer be matched to the subscriber even from a
-- sender seen for the first time.
ALTER TABLE subscriber
    ADD COLUMN pay_token VARCHAR(32),
    ADD COLUMN payment_code VARCHAR(8);

UPDATE subscriber
SET pay_token = replace(CAST(gen_random_uuid() AS TEXT), '-', ''),
    payment_code = upper(substr(replace(CAST(gen_random_uuid() AS TEXT), '-', ''), 1, 6));

ALTER TABLE subscriber
    ALTER COLUMN pay_token SET NOT NULL,
    ALTER COLUMN payment_code SET NOT NULL,
    ADD CONSTRAINT uk_subscriber_pay_token UNIQUE (pay_token),
    ADD CONSTRAINT uk_subscriber_payment_code UNIQUE (payment_code);
