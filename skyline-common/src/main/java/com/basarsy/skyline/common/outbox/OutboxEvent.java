package com.basarsy.skyline.common.outbox;

import java.util.UUID;

/**
 * An unpublished outbox row as the publisher claims it. {@code traceParent} is the W3C trace context of the span
 * that recorded it (see {@link OutboxTracing}), null outside a trace or for rows from before it was kept.
 */
record OutboxEvent(UUID id, UUID aggregateId, String topic, String payload, int attempts, String traceParent) {

    OutboxEvent(UUID id, UUID aggregateId, String topic, String payload, int attempts) {
        this(id, aggregateId, topic, payload, attempts, null);
    }
}
