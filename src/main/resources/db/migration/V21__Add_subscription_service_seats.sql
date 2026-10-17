-- How many seats a family plan has (Spotify Family: 6). Null for an account
-- that is simply shared, like ChatGPT, Claude or Netflix. Display only: a bill
-- is still split among the members, who between them also cover any empty
-- seats.
ALTER TABLE subscription_service ADD COLUMN seats INTEGER;
ALTER TABLE subscription_service ADD CONSTRAINT ck_subscription_service_seats CHECK (seats IS NULL OR seats >= 1);

-- Spotify Family, and three YouTube family plans of six.
UPDATE subscription_service SET seats = 6 WHERE name = 'Spotify';
UPDATE subscription_service SET seats = 18 WHERE name = 'YouTube';
