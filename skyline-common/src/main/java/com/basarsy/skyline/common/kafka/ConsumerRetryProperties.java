package com.basarsy.skyline.common.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * How a consumer retries a record that fails before parking it on {@code <topic>.DLT}: with growing waits that
 * add up to {@code maxElapsedTime} (the attempts themselves come on top, e.g. a database connection timing out),
 * long enough to ride out a database or broker restart. A record that was dead-lettered after a few seconds (the
 * old fixed 3 × 1s) meant a short outage dropped a flight cancellation for good. While a record is retried, the
 * records behind it on its partition wait (per-flight order is kept). A record that can't be read at all, or
 * breaks a database constraint, is parked at once. Each wait stays well under the consumer's
 * {@code max.poll.interval.ms} (5 min), so retrying never triggers a rebalance.
 *
 * @param initialInterval first wait
 * @param multiplier      each wait is this much longer than the one before
 * @param maxInterval     longest single wait
 * @param maxElapsedTime  retrying stops (and the record is parked) once this much time has passed
 */
@Slf4j
@ConfigurationProperties("skyline.kafka.consumer-retry")
public record ConsumerRetryProperties(
        @DefaultValue("1s") Duration initialInterval,
        @DefaultValue("2") double multiplier,
        @DefaultValue("60s") Duration maxInterval,
        @DefaultValue("5m") Duration maxElapsedTime) {

    public ExponentialBackOff backOff() {
        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval.toMillis(), multiplier);
        backOff.setMaxInterval(maxInterval.toMillis());
        backOff.setMaxElapsedTime(maxElapsedTime.toMillis());
        return backOff;
    }

    /**
     * Retries per {@link #backOff()}, then hands the record to {@code deadLetter} with a stable log marker
     * (KAFKA_RECORD_PARKED) to alert on: a parked record is not processed again until someone replays it.
     */
    public DefaultErrorHandler errorHandler(ConsumerRecordRecoverer deadLetter) {
        DefaultErrorHandler handler = new DefaultErrorHandler((record, failure) -> {
            try {
                deadLetter.accept(record, failure);
            } catch (RuntimeException publishFailed) {
                log.error("KAFKA_RECORD_PARK_FAILED topic={} partition={} offset={} key={}: not moved to {}.DLT",
                        record.topic(), record.partition(), record.offset(), record.key(), record.topic(),
                        publishFailed);
                throw publishFailed; // The record is retried rather than lost.
            }
            log.error("KAFKA_RECORD_PARKED topic={} partition={} offset={} key={}: moved to {}.DLT after retrying",
                    record.topic(), record.partition(), record.offset(), record.key(), record.topic(), failure);
        }, backOff());
        // Failures a retry can't fix: malformed JSON (deserialization errors are already not retried) and a
        // constraint the data itself violates.
        handler.addNotRetryableExceptions(JsonProcessingException.class, DataIntegrityViolationException.class);
        return handler;
    }
}
