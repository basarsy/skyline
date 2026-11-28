package com.basarsy.skyline.notification.service;

import com.basarsy.skyline.notification.event.FlightChange;
import com.basarsy.skyline.notification.repository.FlightStateRepository;
import com.basarsy.skyline.notification.repository.Recipient;
import com.basarsy.skyline.notification.repository.RecipientRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells everyone booked on a flight what changed: one inbox entry per account and one email per contact address,
 * each listing the passengers it covers (a family booked together gets one email, not one per passenger).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightChangeHandler {

    private final RecipientRepository recipients;
    private final FlightStateRepository flightStates;
    private final Inbox inbox;

    @Transactional
    public void handle(FlightChange change) {
        if (change.type() == null || change.flightId() == null) {
            log.warn("Ignoring a flight change without type or flight id: {}", change);
            return;
        }
        String key = key(change);
        // One rule for "the flight is cancelled", here and for bookings handled later.
        boolean cancelled = FlightChange.CANCELLED.equals(change.type()) || "CANCELLED".equals(change.status());
        if (change.occurredAt() != null) {
            // For bookings of this flight handled after this change (see BookingEventHandler).
            flightStates.record(change.flightId(), new FlightStateRepository.FlightState(change.flightNumber(),
                    change.departureTime(), cancelled, change.occurredAt(),
                    FlightChange.CANCELLED.equals(change.type()) ? key : null));
        }
        List<Recipient> booked = recipients.activeOnFlight(change.flightId());
        if (FlightChange.RESCHEDULED.equals(change.type()) && change.departureTime() != null) {
            // A reminder sent for the old time stays valid while check-in for the new time was already open at the
            // move; if the new window opens later, check-in is closed again and the passenger is reminded again.
            boolean rearm = change.occurredAt() == null
                    || change.occurredAt().isBefore(change.departureTime().minus(CheckInReminders.OPENS_BEFORE));
            recipients.rescheduleFlight(change.flightId(), change.departureTime(), rearm);
        }
        if (cancelled) {
            recipients.deactivateFlight(change.flightId());
        }
        if (FlightChange.FLIGHT_NUMBER_CHANGED.equals(change.type()) && change.flightNumber() != null) {
            // Later reminders use it. Only from the number it replaced: a replayed older renumber changes nothing.
            recipients.renumberFlight(change.flightId(), change.previousFlightNumber(), change.flightNumber());
        }
        if (Messages.flightChange(change, List.of(), null) == null) {
            return; // Departed, arrived, or a change passengers aren't told about.
        }

        List<List<Recipient>> accounts = groupBy(booked, r -> r.userId() == null ? null : r.userId().toString());
        for (List<Recipient> group : accounts) {
            inbox.toUser(group.get(0).userId(), message(change, group), key);
        }
        for (List<Recipient> group : groupBy(booked,
                r -> r.contactEmail() == null ? null : Inbox.normalised(r.contactEmail()))) {
            inbox.toEmail(group.get(0).contactEmail(), message(change, group), key);
        }
        log.info("Flight {} {}: told {} account(s) about {} booking(s)", change.flightNumber(), change.type(),
                accounts.size(), booked.size());
    }

    /** The dedupe key of a change's messages (one per change, whoever it reaches). */
    static String key(FlightChange change) {
        return "flight:" + change.flightId() + ":" + change.type() + ":" + change.occurredAt();
    }

    private static Message message(FlightChange change, List<Recipient> group) {
        List<String> names = group.stream().map(Recipient::passengerName).distinct().toList();
        return Messages.flightChange(change, names, "/trips/" + group.get(0).reservationId());
    }

    /** Recipients grouped by {@code key}, in first-seen order; those without a key are left out. */
    static List<List<Recipient>> groupBy(List<Recipient> recipients, Function<Recipient, String> key) {
        Map<String, List<Recipient>> groups = new LinkedHashMap<>();
        for (Recipient recipient : recipients) {
            String k = key.apply(recipient);
            if (k != null) {
                groups.computeIfAbsent(k, x -> new ArrayList<>()).add(recipient);
            }
        }
        return List.copyOf(groups.values());
    }
}
