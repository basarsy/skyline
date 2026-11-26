package com.basarsy.skyline.notification.service;

import com.basarsy.skyline.notification.event.BookingEvent;
import com.basarsy.skyline.notification.event.FlightChange;
import com.basarsy.skyline.notification.repository.FlightStateRepository;
import com.basarsy.skyline.notification.repository.FlightStateRepository.FlightState;
import com.basarsy.skyline.notification.repository.RecipientRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the bookings projection and confirms bookings and cancellations to the passenger. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEventHandler {

    private final RecipientRepository recipients;
    private final Inbox inbox;
    private final FlightStateRepository flightStates;

    @Transactional
    public void handle(BookingEvent event) {
        if (event.type() == null || event.bookingId() == null || event.reservations().isEmpty()) {
            log.warn("Ignoring a booking event without type, booking id or reservations: {}", event);
            return;
        }
        switch (event.type()) {
            case BookingEvent.BOOKED -> booked(event);
            case BookingEvent.CANCELLED -> event.reservations().forEach(item -> {
                recipients.cancelled(event.bookingId(), event.userId(), event.contactEmail(), item);
                Message message = Messages.cancelled(item, "/trips/" + item.reservationId());
                String key = "booking:" + item.reservationId() + ":CANCELLED";
                inbox.toUser(event.userId(), message, key);
                inbox.toEmail(event.contactEmail(), message, key);
            });
            default -> log.info("Ignoring booking event type {}", event.type());
        }
    }

    /**
     * A new booking. Its flights' details are as the reservation service saw them when it booked; a flight change
     * that happened after that but was handled first (another topic, no common order) is applied here: the
     * current number and departure, and for a flight cancelled since, an inactive reservation and the
     * cancellation notice its passengers would otherwise never get.
     */
    private void booked(BookingEvent event) {
        Map<UUID, FlightState> states = flightStates.find(
                event.reservations().stream().map(BookingEvent.Item::flightId).filter(Objects::nonNull).toList());
        List<BookingEvent.Item> live = new ArrayList<>();
        Map<UUID, List<BookingEvent.Item>> cancelledFlights = new LinkedHashMap<>();
        for (BookingEvent.Item booked : event.reservations()) {
            FlightState since = changedSince(states.get(booked.flightId()), event.occurredAt());
            BookingEvent.Item item = since == null ? booked : new BookingEvent.Item(booked.reservationId(),
                    booked.pnr(), booked.passengerName(), booked.flightId(),
                    since.flightNumber() != null ? since.flightNumber() : booked.flightNumber(), booked.origin(),
                    booked.destination(), booked.originTimezone(),
                    since.departureTime() != null ? since.departureTime() : booked.departureTime());
            if (since != null && since.cancelled()) {
                recipients.cancelled(event.bookingId(), event.userId(), event.contactEmail(), item);
                cancelledFlights.computeIfAbsent(item.flightId(), id -> new ArrayList<>()).add(item);
            } else {
                recipients.booked(event.bookingId(), event.userId(), event.contactEmail(), item);
                live.add(item);
            }
        }
        if (!live.isEmpty()) {
            BookingEvent current = new BookingEvent(event.type(), event.bookingId(), event.userId(),
                    event.contactEmail(), live, event.occurredAt());
            Message message = Messages.booked(current, "/trips/" + live.get(0).reservationId());
            String key = "booking:" + event.bookingId() + ":BOOKED";
            inbox.toUser(event.userId(), message, key);
            inbox.toEmail(event.contactEmail(), message, key);
        }
        cancelledFlights.forEach((flightId, items) -> {
            BookingEvent.Item first = items.get(0);
            LocalDateTime cancelledAt = states.get(flightId).changedAt();
            FlightChange cancellation = new FlightChange(FlightChange.CANCELLED, flightId, first.flightNumber(),
                    first.origin(), first.destination(), first.originTimezone(), null, "CANCELLED", null,
                    first.departureTime(), first.departureTime(), null, null, null, null, null, cancelledAt);
            Message message = Messages.flightChange(cancellation,
                    items.stream().map(BookingEvent.Item::passengerName).distinct().toList(),
                    "/trips/" + first.reservationId());
            // The flight change's own key: whoever it already reached gets nothing twice.
            String key = "flight:" + flightId + ":" + FlightChange.CANCELLED + ":" + cancelledAt;
            inbox.toUser(event.userId(), message, key);
            inbox.toEmail(event.contactEmail(), message, key);
        });
    }

    /** The flight's state when it changed after {@code bookedAt} (the booking's details are stale), else null. */
    private static FlightState changedSince(FlightState state, LocalDateTime bookedAt) {
        return state != null && bookedAt != null && state.changedAt().isAfter(bookedAt) ? state : null;
    }
}
