package com.basarsy.skyline.common.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the transactional outbox ({@code skyline.outbox.*}).
 *
 * @param batchSize          rows claimed per publisher run
 * @param sendTimeout        how long one send waits for the broker's ack; keep it at least the producer's
 *                           {@code delivery.timeout.ms}, or a row can be marked failed while still being retried
 * @param retryBackoff       delay before the first retry of a failed row, doubled on each further failure
 * @param maxRetryBackoff    upper bound of that delay
 * @param errorAfterAttempts failures after which each further failure is logged at ERROR instead of WARN
 * @param retention          how long published rows are kept before cleanup deletes them
 * @param cleanupBatchSize   rows cleanup deletes per transaction, so it never holds many row locks for long
 */
@ConfigurationProperties("skyline.outbox")
public record OutboxProperties(
        int batchSize,
        Duration sendTimeout,
        Duration retryBackoff,
        Duration maxRetryBackoff,
        int errorAfterAttempts,
        Duration retention,
        int cleanupBatchSize) {

    /** Delay before the next attempt of a row that has now failed {@code attempts} (at least 1) times. */
    Duration backoffAfter(int attempts) {
        int doublings = Math.min(attempts - 1, 30);
        Duration delay = retryBackoff.multipliedBy(1L << doublings);
        return delay.compareTo(maxRetryBackoff) > 0 ? maxRetryBackoff : delay;
    }
}
