-- Wrong passwords per account. The gateway limits login attempts per client IP only, so one account could be
-- guessed at from many addresses; after too many failures in a row the account is locked for a while.
ALTER TABLE users ADD COLUMN failed_logins INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP;
