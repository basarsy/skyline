package com.basarsy.skyline.schedule.notification;

import com.basarsy.skyline.common.notification.config.KafkaTopicConfig;
import com.basarsy.skyline.schedule.event.FlightCancelledEvent;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes a committed flight cancellation to Kafka, where the reservation service cancels the bookings.
 *
 * <p>Runs after commit, so a failure can neither roll the cancellation back nor reach the HTTP caller
 * (Spring logs and swallows exceptions from after-completion callbacks). The send is therefore waited on
 * and retried a bounded number of times; if it still fails, the error is logged with a stable
 * {@code FLIGHT_CANCELLED_PUBLISH_FAILED} marker for alerting and manual re-publication. A transactional
 * outbox would make this fully reliable and is the intended follow-up. Re-sending is harmless: the
 * consumer only cancels reservations that are not cancelled yet.
 */
@Slf4j
@Component
public class KafkaNotificationBridge {

    static final int MAX_ATTEMPTS = 3;
    static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Duration retryDelay;

    @Autowired
    public KafkaNotificationBridge(KafkaTemplate<String, Object> kafkaTemplate) {
        this(kafkaTemplate, Duration.ofMillis(500));
    }

    KafkaNotificationBridge(KafkaTemplate<String, Object> kafkaTemplate, Duration retryDelay) {
        this.kafkaTemplate = kafkaTemplate;
        this.retryDelay = retryDelay;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleFlightCancelled(FlightCancelledEvent event) {
        String key = event.flightId().toString();
        try {
            for (int attempt = 1; ; attempt++) {
                try {
                    kafkaTemplate.send(KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC, key, event)
                            .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                    log.info("Sent FlightCancelledEvent to Kafka for flight: {}", event.flightId());
                    return;
                } catch (ExecutionException | TimeoutException | RuntimeException e) {
                    if (attempt >= MAX_ATTEMPTS) {
                        publishFailed(event, e);
                        return;
                    }
                    log.warn("Sending FlightCancelledEvent for flight {} failed (attempt {}), retrying: {}",
                            event.flightId(), attempt, e.getMessage());
                    Thread.sleep(retryDelay.toMillis());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            publishFailed(event, e);
        }
    }

    private static void publishFailed(FlightCancelledEvent event, Exception cause) {
        log.error("FLIGHT_CANCELLED_PUBLISH_FAILED flightId={}: reservations were not cancelled and need "
                + "re-publication", event.flightId(), cause);
    }
}
