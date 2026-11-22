package com.basarsy.skyline.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.basarsy.skyline.notification.event.BookingEvent;
import com.basarsy.skyline.notification.event.FlightChange;
import com.basarsy.skyline.notification.service.BookingEventHandler;
import com.basarsy.skyline.notification.service.FlightChangeHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** The wire formats of the schedule and reservation services, as their outboxes write them. */
class NotificationListenersTest {

    final FlightChangeHandler flightChanges = mock(FlightChangeHandler.class);
    final BookingEventHandler bookingEvents = mock(BookingEventHandler.class);
    // Spring Boot's ObjectMapper: ISO dates, unknown properties ignored.
    final NotificationListeners listeners =
            new NotificationListeners(Jackson2ObjectMapperBuilder.json().build(), flightChanges, bookingEvents);

    @Test
    void readsAFlightChangedEvent() throws JsonProcessingException {
        listeners.onFlightChange("""
                {"type":"DELAYED","flightId":"7b0c3f7e-2f1a-4c39-9a52-3c1b8f0c1d11","flightNumber":"SK100",
                 "origin":"IST","destination":"LHR","originTimezone":"Europe/Istanbul",
                 "destinationTimezone":"Europe/London","status":"DELAYED","previousStatus":"SCHEDULED",
                 "departureTime":"2030-06-01T09:00:00","previousDepartureTime":"2030-06-01T09:00:00",
                 "arrivalTime":"2030-06-01T13:00:00","estimatedDepartureTime":"2030-06-01T10:30:00",
                 "previousEstimatedDepartureTime":null,"gate":"B12","previousGate":null,
                 "occurredAt":"2030-06-01T06:00:00.123456","somethingAddedLater":42}""");

        ArgumentCaptor<FlightChange> change = ArgumentCaptor.forClass(FlightChange.class);
        verify(flightChanges).handle(change.capture());
        assertThat(change.getValue().type()).isEqualTo("DELAYED");
        assertThat(change.getValue().estimatedDepartureTime()).isEqualTo(LocalDateTime.of(2030, 6, 1, 10, 30));
        assertThat(change.getValue().gate()).isEqualTo("B12");
    }

    @Test
    void anUnknownChangeTypeStillParses() throws JsonProcessingException {
        listeners.onFlightChange("{\"type\":\"DIVERTED\",\"flightId\":\"7b0c3f7e-2f1a-4c39-9a52-3c1b8f0c1d11\"}");

        ArgumentCaptor<FlightChange> change = ArgumentCaptor.forClass(FlightChange.class);
        verify(flightChanges).handle(change.capture());
        assertThat(change.getValue().type()).isEqualTo("DIVERTED");
    }

    @Test
    void readsABookingEventIgnoringFieldsItDoesNotNeed() throws JsonProcessingException {
        listeners.onBookingEvent("""
                {"type":"BOOKED","bookingId":"0a6c1d5e-8f9b-4a1c-b2d3-e4f5a6b7c8d9",
                 "userId":"1b2c3d4e-5f60-4718-8293-a4b5c6d7e8f9","contactEmail":"ada@example.com",
                 "reservations":[{"reservationId":"2c3d4e5f-6071-4829-93a4-b5c6d7e8f901","pnr":"ABC234",
                   "passengerName":"Ada Lovelace","flightId":"7b0c3f7e-2f1a-4c39-9a52-3c1b8f0c1d11",
                   "flightNumber":"SK100","origin":"IST","destination":"LHR","originTimezone":"Europe/Istanbul",
                   "destinationTimezone":"Europe/London","departureTime":"2030-06-01T09:00:00",
                   "arrivalTime":"2030-06-01T13:00:00","distanceKm":2500,"cabinClass":"ECONOMY",
                   "fareType":"FLEX","totalPrice":130.00,"currency":"EUR"}],
                 "occurredAt":"2030-05-01T10:00:00"}""");

        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(bookingEvents).handle(event.capture());
        assertThat(event.getValue().contactEmail()).isEqualTo("ada@example.com");
        assertThat(event.getValue().reservations()).singleElement()
                .satisfies(item -> assertThat(item.pnr()).isEqualTo("ABC234"));
    }

    @Test
    void malformedJsonFailsSoTheRecordIsRetriedThenDeadLettered() {
        assertThatThrownBy(() -> listeners.onBookingEvent("{not json")).isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(bookingEvents);
    }
}
