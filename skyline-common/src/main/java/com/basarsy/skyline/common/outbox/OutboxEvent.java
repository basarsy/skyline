package com.basarsy.skyline.common.outbox;

import java.util.UUID;

/** An unpublished outbox row as the publisher claims it. */
record OutboxEvent(UUID id, UUID aggregateId, String topic, String payload, int attempts) {}
