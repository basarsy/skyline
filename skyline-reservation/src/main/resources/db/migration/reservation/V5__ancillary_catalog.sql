-- The add-on catalog: one generic model for bags, extra weight, sports equipment, priority boarding, meals...
-- A new add-on is a row here plus its prices, not new code (unless it has a new kind of rule).
CREATE TABLE ancillary_products (
    code                VARCHAR(64) PRIMARY KEY,
    type                VARCHAR(32) NOT NULL,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(512) NOT NULL,
    weight_kg           INT,
    max_quantity        INT NOT NULL CHECK (max_quantity > 0),
    -- Online sales stop this many hours before departure (meals need the kitchen's notice).
    cutoff_hours        INT NOT NULL DEFAULT 1,
    -- Refunded on cancellation whatever the fare (otherwise only FLEX fares get add-ons back).
    always_refundable   BOOLEAN NOT NULL DEFAULT FALSE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order          INT NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);

-- Prices by route type. Buying at the airport desk costs more than buying online.
CREATE TABLE ancillary_prices (
    id            UUID PRIMARY KEY,
    product_code  VARCHAR(64) NOT NULL REFERENCES ancillary_products (code),
    route_scope   VARCHAR(16) NOT NULL,
    online_price  NUMERIC(12, 2) NOT NULL CHECK (online_price >= 0),
    airport_price NUMERIC(12, 2) NOT NULL CHECK (airport_price >= 0),
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT uq_ancillary_price UNIQUE (product_code, route_scope)
);

-- What each cabin and fare already includes, so nobody pays twice.
CREATE TABLE fare_allowances (
    id           UUID PRIMARY KEY,
    cabin_class  VARCHAR(32) NOT NULL,
    fare_type    VARCHAR(16) NOT NULL,
    product_code VARCHAR(64) NOT NULL REFERENCES ancillary_products (code),
    quantity     INT NOT NULL CHECK (quantity > 0),
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL,
    CONSTRAINT uq_fare_allowance UNIQUE (cabin_class, fare_type, product_code)
);

INSERT INTO ancillary_products (code, type, name, description, weight_kg, max_quantity, cutoff_hours,
                                always_refundable, sort_order, created_at, updated_at)
VALUES
    ('CABIN_BAG', 'CABIN_BAG', 'Cabin bag', 'A wheeled carry-on up to 8 kg and 55 x 40 x 23 cm, on top of your personal item', 8, 1, 1, FALSE, 10, NOW(), NOW()),
    ('CHECKED_BAG_15', 'CHECKED_BAG', 'Checked bag 15 kg', 'One checked bag up to 15 kg', 15, 3, 1, FALSE, 20, NOW(), NOW()),
    ('CHECKED_BAG_20', 'CHECKED_BAG', 'Checked bag 20 kg', 'One checked bag up to 20 kg', 20, 3, 1, FALSE, 21, NOW(), NOW()),
    ('CHECKED_BAG_30', 'CHECKED_BAG', 'Checked bag 30 kg', 'One checked bag up to 30 kg', 30, 3, 1, FALSE, 22, NOW(), NOW()),
    ('EXTRA_WEIGHT_10', 'EXTRA_WEIGHT', 'Extra 10 kg', 'Adds 10 kg to one of your checked bags', 10, 3, 1, FALSE, 30, NOW(), NOW()),
    ('EXTRA_WEIGHT_20', 'EXTRA_WEIGHT', 'Extra 20 kg', 'Adds 20 kg to one of your checked bags', 20, 3, 1, FALSE, 31, NOW(), NOW()),
    ('SPORTS_EQUIPMENT', 'SPORTS_EQUIPMENT', 'Sports or oversized item', 'Skis, golf clubs, a bike or another item over 158 cm, up to 23 kg', 23, 2, 1, FALSE, 40, NOW(), NOW()),
    ('PRIORITY_BOARDING', 'PRIORITY_BOARDING', 'Priority boarding', 'Board first, with a dedicated lane at the gate', NULL, 1, 1, FALSE, 50, NOW(), NOW()),
    ('MEAL_HOT', 'MEAL', 'Hot meal', 'A hot main course with a drink, pre-ordered', NULL, 1, 24, TRUE, 60, NOW(), NOW()),
    ('MEAL_VEGETARIAN', 'MEAL', 'Vegetarian meal', 'A hot vegetarian main course with a drink, pre-ordered', NULL, 1, 24, TRUE, 61, NOW(), NOW());

INSERT INTO ancillary_prices (id, product_code, route_scope, online_price, airport_price, created_at, updated_at)
SELECT gen_random_uuid(), code, scope, online, airport, NOW(), NOW()
FROM (VALUES
    ('CABIN_BAG', 'DOMESTIC', 15.00, 30.00), ('CABIN_BAG', 'INTERNATIONAL', 25.00, 45.00),
    ('CHECKED_BAG_15', 'DOMESTIC', 20.00, 40.00), ('CHECKED_BAG_15', 'INTERNATIONAL', 30.00, 55.00),
    ('CHECKED_BAG_20', 'DOMESTIC', 25.00, 50.00), ('CHECKED_BAG_20', 'INTERNATIONAL', 40.00, 70.00),
    ('CHECKED_BAG_30', 'DOMESTIC', 40.00, 70.00), ('CHECKED_BAG_30', 'INTERNATIONAL', 60.00, 100.00),
    ('EXTRA_WEIGHT_10', 'DOMESTIC', 15.00, 30.00), ('EXTRA_WEIGHT_10', 'INTERNATIONAL', 25.00, 45.00),
    ('EXTRA_WEIGHT_20', 'DOMESTIC', 25.00, 50.00), ('EXTRA_WEIGHT_20', 'INTERNATIONAL', 40.00, 70.00),
    ('SPORTS_EQUIPMENT', 'DOMESTIC', 35.00, 60.00), ('SPORTS_EQUIPMENT', 'INTERNATIONAL', 50.00, 80.00),
    ('PRIORITY_BOARDING', 'DOMESTIC', 8.00, 12.00), ('PRIORITY_BOARDING', 'INTERNATIONAL', 10.00, 15.00),
    ('MEAL_HOT', 'DOMESTIC', 12.00, 12.00), ('MEAL_HOT', 'INTERNATIONAL', 15.00, 15.00),
    ('MEAL_VEGETARIAN', 'DOMESTIC', 12.00, 12.00), ('MEAL_VEGETARIAN', 'INTERNATIONAL', 15.00, 15.00)
) AS seed (code, scope, online, airport);

-- BASIC: a personal item only. STANDARD: + cabin bag. FLEX: + a 20 kg bag and priority boarding.
-- Business and First (any fare): cabin bag, 30 kg bags, priority boarding and a hot meal.
INSERT INTO fare_allowances (id, cabin_class, fare_type, product_code, quantity, created_at, updated_at)
SELECT gen_random_uuid(), cabin, fare, code, qty, NOW(), NOW()
FROM (VALUES
    ('ECONOMY', 'STANDARD', 'CABIN_BAG', 1),
    ('ECONOMY', 'FLEX', 'CABIN_BAG', 1), ('ECONOMY', 'FLEX', 'CHECKED_BAG_20', 1),
    ('ECONOMY', 'FLEX', 'PRIORITY_BOARDING', 1)
) AS seed (cabin, fare, code, qty);

INSERT INTO fare_allowances (id, cabin_class, fare_type, product_code, quantity, created_at, updated_at)
SELECT gen_random_uuid(), cabin, fare, code, CASE WHEN code = 'CHECKED_BAG_30' THEN bags ELSE 1 END, NOW(), NOW()
FROM (VALUES ('BUSINESS', 2), ('FIRST', 3)) AS c (cabin, bags)
CROSS JOIN (VALUES ('BASIC'), ('STANDARD'), ('FLEX')) AS f (fare)
CROSS JOIN (VALUES ('CABIN_BAG'), ('CHECKED_BAG_30'), ('PRIORITY_BOARDING'), ('MEAL_HOT')) AS p (code);
