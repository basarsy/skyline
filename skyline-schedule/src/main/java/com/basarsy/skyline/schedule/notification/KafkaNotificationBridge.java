package com.basarsy.skyline.schedule.notification;

import com.basarsy.skyline.common.notification.config.KafkaTopicConfig;
import com.basarsy.skyline.schedule.event.FlightCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaNotificationBridge {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleFlightCancelled(FlightCancelledEvent event) {
        log.info("Bridging FlightCancelledEvent to Kafka: {}", event.flightId());
        
        kafkaTemplate.send(KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC, 
                event.flightId().toString(), 
                event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully sent FlightCancelledEvent to Kafka for flight: {}", event.flightId());
                } else {
                    log.error("Failed to send FlightCancelledEvent to Kafka for flight: {}", event.flightId(), ex);
                }
            });
    }
}
