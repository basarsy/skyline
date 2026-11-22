package com.basarsy.skyline.notification.listener;

import com.basarsy.skyline.common.notification.config.KafkaTopicConfig;
import com.basarsy.skyline.notification.event.BookingEvent;
import com.basarsy.skyline.notification.event.FlightChange;
import com.basarsy.skyline.notification.service.BookingEventHandler;
import com.basarsy.skyline.notification.service.FlightChangeHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads both topics as text and parses each into its own shape (one consumer, two record types). Delivery is
 * at least once; the handlers are idempotent. A record that can't be parsed or handled is retried, then parked
 * on its dead-letter topic (see KafkaErrorHandlingConfig).
 */
@Component
@RequiredArgsConstructor
public class NotificationListeners {

    private final ObjectMapper objectMapper;
    private final FlightChangeHandler flightChanges;
    private final BookingEventHandler bookingEvents;

    @KafkaListener(topics = KafkaTopicConfig.FLIGHT_CHANGED_TOPIC)
    public void onFlightChange(String payload) throws JsonProcessingException {
        flightChanges.handle(objectMapper.readValue(payload, FlightChange.class));
    }

    @KafkaListener(topics = KafkaTopicConfig.BOOKING_EVENTS_TOPIC)
    public void onBookingEvent(String payload) throws JsonProcessingException {
        bookingEvents.handle(objectMapper.readValue(payload, BookingEvent.class));
    }
}
