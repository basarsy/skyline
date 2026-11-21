package com.basarsy.skyline.schedule.outbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Sends committed outbox rows to Kafka.
 *
 * <p>Each run claims a batch of due rows with {@code FOR UPDATE SKIP LOCKED}, so several schedule instances
 * can run it at once without sending the same row concurrently. Rows are sent one by one with the aggregate id
 * as the key, each waiting for the broker's ack. A sent row is marked published; the first failure is recorded
 * on its row ({@code attempts}, {@code last_error}, and an exponential backoff before it is due again) and ends
 * the run, since the broker is most likely unreachable for the rest of the batch too. Rows are never given up
 * on: they are retried until Kafka takes them.
 *
 * <p>Delivery is at least once: a crash between the ack and the commit of {@code published_at} sends the row
 * again on the next run. That is safe because the consumer (the reservation service's
 * {@code FlightCancellationListener}) only cancels reservations that are not cancelled yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionOperations transactions;
    private final OutboxProperties properties;

    /** Publishes one batch of due rows and returns how many were sent. */
    public int publishPending() {
        int published = transactions.execute(status -> {
            List<OutboxEvent> batch = repository.claimBatch(properties.batchSize());
            List<UUID> sent = new ArrayList<>(batch.size());
            for (OutboxEvent event : batch) {
                try {
                    send(event);
                    sent.add(event.id());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    recordFailure(event, e);
                    break;
                }
            }
            repository.markPublished(sent);
            return sent.size();
        });
        if (published > 0) {
            log.info("Published {} outbox event(s) to Kafka", published);
        }
        return published;
    }

    /** Deletes published rows older than the retention and returns how many were deleted. */
    public int deletePublished() {
        return transactions.execute(status -> repository.deletePublishedBefore(properties.retention()));
    }

    private void send(OutboxEvent event) throws Exception {
        try {
            kafkaTemplate.send(event.topic(), event.aggregateId().toString(), event.payload())
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private void recordFailure(OutboxEvent event, Exception cause) {
        int attempts = event.attempts() + 1;
        Duration backoff = properties.backoffAfter(attempts);
        repository.markFailed(event.id(), attempts, cause.toString(), backoff);
        // ERROR (with a stable marker for alerting) once a row keeps failing; it is still retried.
        boolean stuck = attempts >= properties.errorAfterAttempts();
        log.atLevel(stuck ? Level.ERROR : Level.WARN).log(
                "{}Publishing outbox event {} for {} to {} failed (attempt {}), retrying in {}: {}",
                stuck ? "OUTBOX_PUBLISH_FAILING " : "", event.id(), event.aggregateId(), event.topic(), attempts,
                backoff, cause.toString());
    }
}
