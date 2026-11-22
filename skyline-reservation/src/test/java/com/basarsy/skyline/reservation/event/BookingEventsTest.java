package com.basarsy.skyline.reservation.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.basarsy.skyline.common.notification.config.KafkaTopicConfig;
import com.basarsy.skyline.common.outbox.Outbox;
import com.basarsy.skyline.reservation.client.dto.AirportSummary;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.RouteResponse;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.entity.Passenger;
import com.basarsy.skyline.reservation.entity.Reservation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BookingEventsTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-01T10:00:00Z"), ZoneOffset.UTC);
    static final UUID USER_ID = UUID.randomUUID();

    final Outbox outbox = mock(Outbox.class);
    final BookingEvents events = new BookingEvents(outbox, CLOCK);

    @Test
    void aGroupBookingIsOneEventKeyedByTheGroupWithEveryReservation() {
        UUID groupId = UUID.randomUUID();
        Reservation ada = reservation("Ada", groupId);
        Reservation alan = reservation("Alan", groupId);
        FlightResponse flight = flight();

        events.booked(groupId, List.of(new BookingEvents.Booked(ada, flight), new BookingEvents.Booked(alan, flight)));

        BookingEvent event = appended(groupId);
        assertThat(event.type()).isEqualTo(BookingEventType.BOOKED);
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.contactEmail()).isEqualTo("ada@example.com");
        assertThat(event.occurredAt()).isEqualTo(LocalDateTime.of(2030, 5, 1, 10, 0));
        assertThat(event.reservations()).extracting(BookingEvent.Item::passengerName)
                .containsExactly("Ada Lovelace", "Alan Lovelace");
        BookingEvent.Item item = event.reservations().get(0);
        assertThat(item.flightNumber()).isEqualTo("SK100");
        assertThat(item.origin()).isEqualTo("IST");
        assertThat(item.destinationTimezone()).isEqualTo("Europe/London");
        assertThat(item.distanceKm()).isEqualTo(2500);
        assertThat(item.cabinClass()).isEqualTo(CabinClass.ECONOMY);
        assertThat(item.fareType()).isEqualTo(FareType.FLEX);
        assertThat(item.currency()).isEqualTo("EUR");
    }

    @Test
    void aCancellationOfASingleBookingIsKeyedByTheReservation() {
        Reservation ada = reservation("Ada", null);

        events.cancelled(ada, null);

        BookingEvent event = appended(ada.getId());
        assertThat(event.type()).isEqualTo(BookingEventType.CANCELLED);
        assertThat(event.reservations()).singleElement().satisfies(item -> {
            assertThat(item.pnr()).isEqualTo("ABC234");
            assertThat(item.flightNumber()).as("flight unavailable").isNull();
        });
    }

    BookingEvent appended(UUID key) {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outbox).append(eq(KafkaTopicConfig.BOOKING_EVENTS_TOPIC), eq(key), event.capture());
        return (BookingEvent) event.getValue();
    }

    static Reservation reservation(String firstName, UUID groupId) {
        Passenger passenger = new Passenger();
        passenger.setUserId(USER_ID);
        passenger.setFirstName(firstName);
        passenger.setLastName("Lovelace");
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setPassenger(passenger);
        reservation.setPnr("ABC234");
        reservation.setFlightId(UUID.randomUUID());
        reservation.setBookingGroupId(groupId);
        reservation.setContactEmail("ada@example.com");
        reservation.setCabinClass(CabinClass.ECONOMY);
        reservation.setFareType(FareType.FLEX);
        reservation.setTotalPrice(new BigDecimal("130.00"));
        return reservation;
    }

    static FlightResponse flight() {
        return FlightResponse.builder()
                .id(UUID.randomUUID()).flightNumber("SK100").currency("EUR")
                .departureTime(LocalDateTime.of(2030, 6, 1, 9, 0)).arrivalTime(LocalDateTime.of(2030, 6, 1, 13, 0))
                .route(new RouteResponse(UUID.randomUUID(),
                        new AirportSummary(UUID.randomUUID(), "IST", "Istanbul", "Istanbul", "Turkey", "Europe/Istanbul"),
                        new AirportSummary(UUID.randomUUID(), "LHR", "Heathrow", "London", "UK", "Europe/London"),
                        2500, 240))
                .build();
    }
}
