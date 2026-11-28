package com.basarsy.skyline.notification.service;

import com.basarsy.skyline.notification.event.BookingEvent;
import com.basarsy.skyline.notification.event.FlightChange;
import com.basarsy.skyline.notification.repository.FlightStateRepository;
import com.basarsy.skyline.notification.repository.FlightStateRepository.FlightState;
import com.basarsy.skyline.notification.repository.RecipientRepository;
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
     * cancellation notice its passengers would otherwise never get. The confirmation shows each flight's delay
     * and gate in force, whose notices went out before these passengers were recipients.
     *
     * <p>The latest handled change is applied whatever its time: timestamps from two services can't say which came
     * first. Should the booking be newer than it (a later change still on its way), that change updates the
     * booking's details when it is handled, as it does every booking of the flight.
     */
    private void booked(BookingEvent event) {
        List<UUID> flightIds =
                event.reservations().stream().map(BookingEvent.Item::flightId).filter(Objects::nonNull).toList();
        flightStates.lock(flightIds); // against a change of these flights handled meanwhile
        Map<UUID, FlightState> states = flightStates.find(flightIds);
        List<BookingEvent.Item> live = new ArrayList<>();
        Map<UUID, List<BookingEvent.Item>> cancelledFlights = new LinkedHashMap<>();
        for (BookingEvent.Item booked : event.reservations()) {
            FlightState state = states.get(booked.flightId());
            BookingEvent.Item item = state == null ? booked : new BookingEvent.Item(booked.reservationId(),
                    booked.pnr(), booked.passengerName(), booked.flightId(),
                    state.flightNumber() != null ? state.flightNumber() : booked.flightNumber(), booked.origin(),
                    booked.destination(), booked.originTimezone(),
                    state.departureTime() != null ? state.departureTime() : booked.departureTime());
            if (state != null && state.cancelled()) {
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
            Message message = Messages.booked(current, "/trips/" + live.get(0).reservationId(), states);
            String key = "booking:" + event.bookingId() + ":BOOKED";
            inbox.toUser(event.userId(), message, key);
            inbox.toEmail(event.contactEmail(), message, key);
        }
        cancelledFlights.forEach((flightId, items) -> {
            BookingEvent.Item first = items.get(0);
            FlightState state = states.get(flightId);
            FlightChange cancellation = new FlightChange(FlightChange.CANCELLED, flightId, first.flightNumber(),
                    first.origin(), first.destination(), first.originTimezone(), null, "CANCELLED", null,
                    first.departureTime(), first.departureTime(), null, null, null, null, null, state.changedAt());
            Message message = Messages.flightChange(cancellation,
                    items.stream().map(BookingEvent.Item::passengerName).distinct().toList(),
                    "/trips/" + first.reservationId());
            // The flight change's own key, as it built it: whoever it already reached gets nothing twice.
            String key = state.cancellationKey() != null ? state.cancellationKey()
                    : FlightChangeHandler.key(cancellation);
            inbox.toUser(event.userId(), message, key);
            inbox.toEmail(event.contactEmail(), message, key);
        });
    }
}
