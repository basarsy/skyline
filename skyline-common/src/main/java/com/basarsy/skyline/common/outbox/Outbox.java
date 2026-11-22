package com.basarsy.skyline.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records an event for Kafka in the caller's transaction; {@link OutboxPublisher} sends it after commit. If the
 * transaction rolls back the event is never sent, and once it commits the event is sent at least once.
 */
@RequiredArgsConstructor
public class Outbox {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Stores {@code event} as JSON, to be sent to {@code topic} with {@code aggregateId} as the record key. The
     * stored JSON is sent as is: the value on the wire is exactly this serialization, with no type headers.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, UUID aggregateId, Object event) {
        try {
            repository.insert(aggregateId, event.getClass().getSimpleName(), topic,
                    objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize " + event.getClass().getSimpleName(), e);
        }
    }
}
