-- Seat maps as data: each aircraft type carries a compact cabin layout (see CabinLayout / SeatLayoutExpander),
-- expanded into seats in code. Its seat count must equal total_seats, which the service checks on every save.
ALTER TABLE aircraft_types ADD COLUMN cabin_layout JSONB;

-- A narrow-body with two cabins, so the seeds cover 1-cabin narrow, 2-cabin narrow and 2-cabin wide.
INSERT INTO aircraft_types (id, manufacturer, model, total_seats, cabin_config, created_at, updated_at)
VALUES ('22222222-2222-2222-2222-222222222204', 'Airbus', 'A321neo', 190, 'J16Y174', NOW(), NOW());

-- A320neo, Y180: 3-3, rows 1-31 without 13, two overwing exit rows (11, 12).
UPDATE aircraft_types SET cabin_layout = '{
  "body": "NARROW",
  "sections": [
    {"cabin": "ECONOMY", "fromRow": 1, "toRow": 31, "columns": "ABC|DEF", "frontRows": 4, "extraLegroomRows": []}
  ],
  "skipRows": [13],
  "exitRows": [11, 12],
  "wing": {"fromRow": 8, "toRow": 17},
  "facilities": [
    {"type": "GALLEY", "afterRow": 0, "side": "LEFT"},
    {"type": "LAVATORY", "afterRow": 0, "side": "RIGHT"},
    {"type": "LAVATORY", "afterRow": 31, "side": "LEFT"},
    {"type": "LAVATORY", "afterRow": 31, "side": "RIGHT"},
    {"type": "GALLEY", "afterRow": 31, "side": "FULL"}
  ],
  "omitSeats": [],
  "accessibleSeats": ["2C", "2D", "20C", "20D"]
}'::jsonb WHERE id = '22222222-2222-2222-2222-222222222201';

-- 737-800, Y189: 3-3 over 32 rows, row 1 only D-F (forward door and galley), overwing exits at 16 and 17.
UPDATE aircraft_types SET cabin_layout = '{
  "body": "NARROW",
  "sections": [
    {"cabin": "ECONOMY", "fromRow": 1, "toRow": 32, "columns": "ABC|DEF", "frontRows": 3, "extraLegroomRows": []}
  ],
  "skipRows": [],
  "exitRows": [16, 17],
  "wing": {"fromRow": 12, "toRow": 21},
  "facilities": [
    {"type": "LAVATORY", "afterRow": 0, "side": "LEFT"},
    {"type": "GALLEY", "afterRow": 0, "side": "RIGHT"},
    {"type": "LAVATORY", "afterRow": 32, "side": "LEFT"},
    {"type": "LAVATORY", "afterRow": 32, "side": "RIGHT"},
    {"type": "GALLEY", "afterRow": 32, "side": "FULL"}
  ],
  "omitSeats": ["1A", "1B", "1C"],
  "accessibleSeats": ["3C", "3D"]
}'::jsonb WHERE id = '22222222-2222-2222-2222-222222222202';

-- 787-9, C30 Y266: business 2-2-2 in rows 1-5, economy 3-3-3 in rows 10-39 with the tail narrowing the last two
-- rows, exit doors at 10 and 25.
UPDATE aircraft_types SET cabin_layout = '{
  "body": "WIDE",
  "sections": [
    {"cabin": "BUSINESS", "fromRow": 1, "toRow": 5, "columns": "AC|DG|HK", "frontRows": 0, "extraLegroomRows": []},
    {"cabin": "ECONOMY", "fromRow": 10, "toRow": 39, "columns": "ABC|DEF|HJK", "frontRows": 4, "extraLegroomRows": []}
  ],
  "skipRows": [],
  "exitRows": [10, 25],
  "wing": {"fromRow": 14, "toRow": 26},
  "facilities": [
    {"type": "GALLEY", "afterRow": 0, "side": "CENTER"},
    {"type": "LAVATORY", "afterRow": 0, "side": "LEFT"},
    {"type": "LAVATORY", "afterRow": 0, "side": "RIGHT"},
    {"type": "GALLEY", "afterRow": 5, "side": "CENTER"},
    {"type": "LAVATORY", "afterRow": 5, "side": "LEFT"},
    {"type": "LAVATORY", "afterRow": 5, "side": "RIGHT"},
    {"type": "LAVATORY", "afterRow": 24, "side": "LEFT"},
    {"type": "LAVATORY", "afterRow": 24, "side": "RIGHT"},
    {"type": "CLOSET", "afterRow": 24, "side": "CENTER"},
    {"type": "LAVATORY", "afterRow": 39, "side": "LEFT"},
    {"type": "LAVATORY", "afterRow": 39, "side": "RIGHT"},
    {"type": "GALLEY", "afterRow": 39, "side": "FULL"}
  ],
  "omitSeats": ["38A", "38K", "39A", "39K"],
  "accessibleSeats": ["10C", "10H", "1C", "1H"]
}'::jsonb WHERE id = '22222222-2222-2222-2222-222222222203';

-- A321neo, J16 Y174: business 2-2 in rows 1-4, economy 3-3 in rows 5-33, exits at 12 (overwing) and 26 (door).
UPDATE aircraft_types SET cabin_layout = '{
  "body": "NARROW",
  "sections": [
    {"cabin": "BUSINESS", "fromRow": 1, "toRow": 4, "columns": "AC|DF", "frontRows": 0, "extraLegroomRows": []},
    {"cabin": "ECONOMY", "fromRow": 5, "toRow": 33, "columns": "ABC|DEF", "frontRows": 3, "extraLegroomRows": [27]}
  ],
  "skipRows": [],
  "exitRows": [12, 26],
  "wing": {"fromRow": 10, "toRow": 19},
  "facilities": [
    {"type": "GALLEY", "afterRow": 0, "side": "LEFT"},
    {"type": "LAVATORY", "afterRow": 0, "side": "RIGHT"},
    {"type": "LAVATORY", "afterRow": 4, "side": "LEFT"},
    {"type": "LAVATORY", "afterRow": 33, "side": "LEFT"},
    {"type": "LAVATORY", "afterRow": 33, "side": "RIGHT"},
    {"type": "GALLEY", "afterRow": 33, "side": "FULL"}
  ],
  "omitSeats": [],
  "accessibleSeats": ["5C", "5D"]
}'::jsonb WHERE id = '22222222-2222-2222-2222-222222222204';
