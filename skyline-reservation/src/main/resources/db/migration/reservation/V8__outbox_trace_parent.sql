-- The W3C trace context (traceparent) of the request that recorded each event, so its Kafka record continues
-- that trace instead of starting a new one when the publisher sends it later (see OutboxTracing).
ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(55);
