-- Who travels on which flight, as the booking events told us (a projection of the reservation service's data):
-- flight changes are addressed to the active rows of their flight. Bookings made before this service existed are
-- not in it.
CREATE TABLE booking_recipients (
    reservation_id           UUID PRIMARY KEY,
    booking_id               UUID NOT NULL,
    flight_id                UUID NOT NULL,
    user_id                  UUID,
    contact_email            VARCHAR(254),
    passenger_name           VARCHAR(255) NOT NULL,
    pnr                      VARCHAR(6) NOT NULL,
    flight_number            VARCHAR(16),
    origin                   VARCHAR(3),
    destination              VARCHAR(3),
    origin_timezone          VARCHAR(64),
    departure_time           TIMESTAMP,
    active                   BOOLEAN NOT NULL,
    check_in_reminded_at     TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_booking_recipients_flight ON booking_recipients (flight_id) WHERE active;
-- The reminder job looks for active bookings whose check-in window has opened and that were not reminded yet.
CREATE INDEX idx_booking_recipients_reminder ON booking_recipients (departure_time)
    WHERE active AND check_in_reminded_at IS NULL;

-- The in-app inbox of each user. dedupe_key makes a redelivered event a no-op.
CREATE TABLE notifications (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    kind        VARCHAR(32) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT NOT NULL,
    link        VARCHAR(255),
    dedupe_key  VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at     TIMESTAMPTZ
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

-- Emails to send. Written in the transaction that handles an event and sent afterwards by EmailSender, so an SMTP
-- outage delays emails instead of failing (and dead-lettering) the event.
CREATE TABLE email_outbox (
    id               UUID PRIMARY KEY,
    seq              BIGINT GENERATED ALWAYS AS IDENTITY,
    recipient        VARCHAR(254) NOT NULL,
    subject          VARCHAR(255) NOT NULL,
    body             TEXT NOT NULL,
    dedupe_key       VARCHAR(255) NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ,
    attempts         INT NOT NULL DEFAULT 0,
    last_error       TEXT
);
CREATE INDEX idx_email_outbox_pending ON email_outbox (next_attempt_at, seq) WHERE sent_at IS NULL;
