package com.basarsy.skyline.reservation.event;

import com.basarsy.skyline.common.notification.config.KafkaTopicConfig;
import com.basarsy.skyline.common.outbox.Outbox;
import com.basarsy.skyline.reservation.client.dto.AirportSummary;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.RouteResponse;
import com.basarsy.skyline.reservation.entity.Reservation;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records {@link BookingEvent}s through the outbox, in the transaction of the booking change. */
@Component
@RequiredArgsConstructor
public class BookingEvents {

    private final Outbox outbox;
    private final Clock clock;

    /** A confirmed booking: every reservation in it, with the flight each one is on. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void booked(UUID bookingId, List<Booked> reservations) {
        if (reservations.isEmpty()) {
            return;
        }
        Reservation first = reservations.get(0).reservation();
        List<BookingEvent.Item> items = reservations.stream().map(b -> item(b.reservation(), b.flight())).toList();
        append(new BookingEvent(BookingEventType.BOOKED, bookingId, first.getPassenger().getUserId(),
                first.getContactEmail(), items, LocalDateTime.now(clock)));
    }

    /** A reservation the passenger (or staff) cancelled. {@code flight} may be null if it could not be loaded. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelled(Reservation reservation, FlightResponse flight) {
        append(new BookingEvent(BookingEventType.CANCELLED, bookingIdOf(reservation),
                reservation.getPassenger().getUserId(), reservation.getContactEmail(),
                List.of(item(reservation, flight)), LocalDateTime.now(clock)));
    }

    /** The booking group, or the reservation itself when it was booked alone: the key of its events. */
    public static UUID bookingIdOf(Reservation reservation) {
        return reservation.getBookingGroupId() != null ? reservation.getBookingGroupId() : reservation.getId();
    }

    private void append(BookingEvent event) {
        outbox.append(KafkaTopicConfig.BOOKING_EVENTS_TOPIC, event.bookingId(), event);
    }

    private static BookingEvent.Item item(Reservation reservation, FlightResponse flight) {
        RouteResponse route = flight == null ? null : flight.getRoute();
        AirportSummary origin = route == null ? null : route.origin();
        AirportSummary destination = route == null ? null : route.destination();
        return new BookingEvent.Item(
                reservation.getId(),
                reservation.getPnr(),
                reservation.getPassenger().getFirstName() + " " + reservation.getPassenger().getLastName(),
                reservation.getFlightId(),
                flight == null ? null : flight.getFlightNumber(),
                origin == null ? null : origin.iataCode(),
                destination == null ? null : destination.iataCode(),
                origin == null ? null : origin.timezone(),
                destination == null ? null : destination.timezone(),
                flight == null ? null : flight.getDepartureTime(),
                flight == null ? null : flight.getArrivalTime(),
                route == null ? null : route.distanceKm(),
                reservation.getCabinClass(),
                reservation.getFareType(),
                reservation.getTotalPrice(),
                flight == null ? null : flight.getCurrency());
    }

    /** A reservation of a booking and the flight it is on. */
    public record Booked(Reservation reservation, FlightResponse flight) {}
}
