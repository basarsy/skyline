ALTER TABLE crew_members ADD COLUMN first_name VARCHAR(128);
ALTER TABLE crew_members ADD COLUMN last_name VARCHAR(128);

-- Since these were added after the table creation, we should make them NOT NULL if possible.
-- But if there are already records, we might need a default or allow nulls temporarily.
-- For a new project, we can just make them NOT NULL if we are sure there's no data or we don't care about existing data in dev.
-- However, standard practice is to add them as nullable, update, then make not null.
-- For simplicity here:
UPDATE crew_members SET first_name = 'Unknown', last_name = 'Unknown' WHERE first_name IS NULL;

ALTER TABLE crew_members ALTER COLUMN first_name SET NOT NULL;
ALTER TABLE crew_members ALTER COLUMN last_name SET NOT NULL;
