-- A service's price as the merchant states it, in the merchant's currency:
-- Spotify is $7.99, not whatever the card converted it to last month. What a
-- service costs in hryvnias is its latest charge, which comes from the card.
-- Only a UAH price can be a fixed monthly charge.
ALTER TABLE subscription_service ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'UAH';
ALTER TABLE subscription_service ADD CONSTRAINT ck_subscription_service_currency CHECK (currency ~ '^[A-Z]{3}$');
ALTER TABLE subscription_service ADD CONSTRAINT ck_subscription_service_fixed_uah
    CHECK (NOT fixed_monthly_charge OR currency = 'UAH');

UPDATE subscription_service service
SET price = plan.price, currency = plan.currency
FROM (VALUES ('ChatGPT', 100.00, 'USD'),
             ('Claude', 200.00, 'USD'),
             ('Netflix', 9.99, 'EUR'),
             ('Spotify', 7.99, 'USD')) AS plan (name, price, currency)
WHERE service.name = plan.name;
