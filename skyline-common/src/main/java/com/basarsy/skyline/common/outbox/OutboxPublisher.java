package com.basarsy.skyline.common.outbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Sends committed outbox rows to Kafka.
 *
 * <p>Each run claims a batch of due rows with {@code FOR UPDATE SKIP LOCKED}, so several schedule instances
 * can run it at once without sending the same row concurrently. Every row of the batch is handed to the
 * producer first (aggregate id as the key), then the acks are awaited in claim order, all within one
 * {@code sendTimeout}. The rows acked before the first failure are marked published; the first failure is
 * recorded on its row ({@code attempts}, {@code last_error}, and an exponential backoff before it is due again)
 * and ends the run, since the broker is most likely unreachable for the rest of the batch too. Rows are never
 * given up on: they are retried until Kafka takes them.
 *
 * <p>Delivery is at least once: a crash between the ack and the commit of {@code published_at} sends the row
 * again on the next run, and so does a run that stops at a failure while later rows of its batch were still in
 * flight (those may reach Kafka anyway and are sent again). That is safe because the consumer (the reservation
 * service's {@code FlightCancellationListener}) only cancels reservations that are not cancelled yet. Order per
 * key holds because a batch never holds two rows of one aggregate (see {@link OutboxRepository#claimBatch}).
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionOperations transactions;
    private final OutboxProperties properties;
    private final OutboxTracing tracing;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate,
            TransactionOperations transactions, OutboxProperties properties) {
        this(repository, kafkaTemplate, transactions, properties, OutboxTracing.NONE);
    }

    /** Publishes one batch of due rows and returns how many were sent. */
    public int publishPending() {
        int published = transactions.execute(status -> {
            List<OutboxEvent> batch = repository.claimBatch(properties.batchSize());
            List<Future<?>> acks = sendAll(batch);
            long deadline = System.nanoTime() + properties.sendTimeout().toNanos();
            List<UUID> sent = new ArrayList<>(acks.size());
            for (int i = 0; i < acks.size(); i++) {
                OutboxEvent event = batch.get(i);
                try {
                    acks.get(i).get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    sent.add(event.id());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException | TimeoutException e) {
                    recordFailure(event, e instanceof ExecutionException && e.getCause() instanceof Exception cause
                            ? cause : e);
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

    /**
     * Deletes published rows older than the retention, {@code cleanupBatchSize} per transaction, and returns how
     * many were deleted.
     */
    public int deletePublished() {
        int total = 0;
        int deleted;
        do {
            deleted = transactions.execute(status ->
                    repository.deletePublishedBefore(properties.retention(), properties.cleanupBatchSize()));
            total += deleted;
        } while (deleted == properties.cleanupBatchSize());
        return total;
    }

    /**
     * Hands the rows to the producer in order. A send that throws (e.g. no metadata within {@code max.block.ms})
     * stands for its row as a failed ack and stops the sending: the rest would most likely block just as long.
     */
    private List<Future<?>> sendAll(List<OutboxEvent> batch) {
        List<Future<?>> acks = new ArrayList<>(batch.size());
        for (OutboxEvent event : batch) {
            try {
                // In the trace of the request that recorded the event: the record carries it to the consumer.
                acks.add(tracing.continuing(event.traceParent(),
                        () -> kafkaTemplate.send(event.topic(), event.aggregateId().toString(), event.payload())));
            } catch (RuntimeException e) {
                acks.add(CompletableFuture.failedFuture(e));
                break;
            }
        }
        return acks;
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
