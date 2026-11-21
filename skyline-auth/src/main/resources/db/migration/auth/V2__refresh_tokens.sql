-- Server-side record of every issued refresh token, keyed by its jti. A login starts a family; each refresh
-- revokes the presented token (replaced_at set) and issues the next one in the same family. Presenting a
-- token that was replaced long ago means it leaked, and the whole family is revoked.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   UUID      NOT NULL,
    issued_at   TIMESTAMP NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    revoked_at  TIMESTAMP,
    replaced_at TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
