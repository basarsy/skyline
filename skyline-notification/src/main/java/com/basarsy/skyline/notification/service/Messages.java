package com.basarsy.skyline.notification.service;

import com.basarsy.skyline.notification.event.BookingEvent;
import com.basarsy.skyline.notification.event.FlightChange;
import com.basarsy.skyline.notification.repository.FlightStateRepository.FlightState;
import com.basarsy.skyline.notification.repository.Recipient;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** The wording of every message. Times are shown in the departure airport's local time. */
final class Messages {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private Messages() {}

    /**
     * The message for a flight change, or null when passengers aren't told about it (departed, arrived, or a
     * type this version does not know). {@code passengers} are the names the message is for.
     */
    static Message flightChange(FlightChange change, List<String> passengers, String path) {
        String flight = change.flightNumber() == null ? "Your flight" : change.flightNumber();
        String route = route(change.origin(), change.destination());
        String zone = change.originTimezone();
        String who = "Passengers: " + String.join(", ", passengers) + ".";
        return switch (change.type()) {
            case FlightChange.DELAYED -> {
                String now = time(change.estimatedDepartureTime(), zone);
                String delay = delay(change.departureTime(), change.estimatedDepartureTime());
                yield new Message("FLIGHT_DELAYED", flight + " is delayed",
                        "Now departs " + now + " (scheduled " + time(change.departureTime(), zone) + ").",
                        flight + route + " on " + day(change.departureTime(), zone) + " is delayed" + delay
                                + ". It is now expected to depart at " + now + " instead of "
                                + time(change.departureTime(), zone) + " (local time). Check-in and bag drop"
                                + " close at their usual times.\n\n" + who,
                        path, true);
            }
            case FlightChange.DELAY_CLEARED -> new Message("FLIGHT_ON_TIME", flight + " is back on schedule",
                    "Departs at " + time(change.departureTime(), zone) + " as scheduled.",
                    flight + route + " on " + day(change.departureTime(), zone) + " is back on schedule and"
                            + " departs at " + time(change.departureTime(), zone) + " (local time).\n\n" + who,
                    path, true);
            case FlightChange.RESCHEDULED -> new Message("FLIGHT_RESCHEDULED", flight + " has a new time",
                    "Now departs " + day(change.departureTime(), zone) + " at " + time(change.departureTime(), zone)
                            + ".",
                    flight + route + " has been rescheduled. It now departs on " + day(change.departureTime(), zone)
                            + " at " + time(change.departureTime(), zone) + " (local time), instead of "
                            + day(change.previousDepartureTime(), zone) + " at "
                            + time(change.previousDepartureTime(), zone) + ".\n\n" + who,
                    path, true);
            case FlightChange.GATE_CHANGED -> {
                String was = change.previousGate() == null ? "" : " (was " + change.previousGate() + ")";
                String verb = change.previousGate() == null ? "departs from gate " : "now departs from gate ";
                yield new Message("FLIGHT_GATE", flight + ": gate " + change.gate(),
                        "Departs from gate " + change.gate() + was + ".",
                        flight + route + " on " + day(change.departureTime(), zone) + " " + verb + change.gate()
                                + was + ". Boarding starts 45 minutes before departure ("
                                + time(change.expectedDeparture().minusMinutes(45), zone) + ").\n\n" + who,
                        path, true);
            }
            case FlightChange.CANCELLED -> new Message("FLIGHT_CANCELLED", flight + " is cancelled",
                    "Your booking is cancelled and every add-on is refunded.",
                    "We're sorry: " + flight + route + " on " + day(change.departureTime(), zone)
                            + " has been cancelled. Your booking is cancelled and every add-on you bought is"
                            + " refunded.\n\n" + who,
                    path, true);
            case FlightChange.FLIGHT_NUMBER_CHANGED -> {
                if (change.flightNumber() == null) {
                    yield null; // Nothing to tell without the new number.
                }
                String was = change.previousFlightNumber() == null ? "Your flight" : change.previousFlightNumber();
                yield new Message("FLIGHT_NUMBER_CHANGED", was + " is now " + flight,
                        "Same flight, new number: " + flight + ".",
                        was + route + " on " + day(change.departureTime(), zone) + " now flies as " + flight
                                + ". Nothing else changes: your booking, seat and times stay the same. Look for "
                                + flight + " on the departure boards.\n\n" + who,
                        path, true);
            }
            case FlightChange.STATUS_CHANGED -> switch (String.valueOf(change.status())) {
                case "DELAYED" -> new Message("FLIGHT_DELAYED", flight + " is delayed",
                        "A new departure time will follow.",
                        flight + route + " on " + day(change.departureTime(), zone) + " is delayed. We'll let you"
                                + " know the new departure time as soon as we have it.\n\n" + who,
                        path, true);
                case "BOARDING" -> new Message("FLIGHT_BOARDING", flight + " is boarding",
                        "Boarding has started" + (change.gate() == null ? "." : " at gate " + change.gate() + "."),
                        flight + " is boarding.", path, false);
                default -> null;
            };
            default -> null;
        };
    }

    /**
     * A confirmed booking: every passenger on every flight of it, with the delay and gate in force from
     * {@code flights} (the flights' latest states, by id; those without one show neither).
     */
    static Message booked(BookingEvent event, String path, Map<UUID, FlightState> flights) {
        List<BookingEvent.Item> items = event.reservations();
        String pnrs = items.stream().map(BookingEvent.Item::pnr).distinct().collect(Collectors.joining(", "));
        String lines = items.stream()
                .map(item -> "- " + nullToDash(item.flightNumber()) + route(item.origin(), item.destination())
                        + ", " + day(item.departureTime(), item.originTimezone()) + " "
                        + time(item.departureTime(), item.originTimezone())
                        + inForce(flights.get(item.flightId()), item.originTimezone()) + ": " + item.passengerName()
                        + " (booking reference " + item.pnr() + ")")
                .collect(Collectors.joining("\n"));
        return new Message("BOOKING_CONFIRMED", "Booking confirmed: " + pnrs,
                items.size() == 1 ? "Your trip is booked." : items.size() + " reservations are booked.",
                "Thank you for booking with Skyline. Your booking is confirmed:\n\n" + lines
                        + "\n\nCheck-in opens 48 hours before departure; we'll remind you.",
                path, true);
    }

    /** ", delayed to 13:35, gate B12": what a flight's state adds to its scheduled time, or nothing. */
    private static String inForce(FlightState flight, String zone) {
        if (flight == null) {
            return "";
        }
        return (flight.estimatedDepartureTime() == null ? ""
                        : ", delayed to " + time(flight.estimatedDepartureTime(), zone))
                + (flight.gate() == null ? "" : ", gate " + flight.gate());
    }

    static Message cancelled(BookingEvent.Item item, String path) {
        return new Message("BOOKING_CANCELLED", "Booking " + item.pnr() + " cancelled",
                item.passengerName() + " is no longer booked on " + nullToDash(item.flightNumber()) + ".",
                "Your reservation " + item.pnr() + " for " + item.passengerName() + " on "
                        + nullToDash(item.flightNumber()) + route(item.origin(), item.destination()) + ", "
                        + day(item.departureTime(), item.originTimezone()) + ", is cancelled.",
                path, true);
    }

    static Message checkInOpen(List<Recipient> booking, String path) {
        Recipient first = booking.get(0);
        String names = booking.stream().map(Recipient::passengerName).collect(Collectors.joining(", "));
        return new Message("CHECK_IN_OPEN", "Check-in is open for " + nullToDash(first.flightNumber()),
                "Check in now and get your boarding pass.",
                "Online check-in is open for " + nullToDash(first.flightNumber())
                        + route(first.origin(), first.destination()) + ", departing "
                        + day(first.departureTime(), first.originTimezone()) + " at "
                        + time(first.departureTime(), first.originTimezone())
                        + " (local time). It closes 1 hour before departure.\n\nPassengers: " + names + ".",
                path, true);
    }

    private static String route(String origin, String destination) {
        return origin == null || destination == null ? "" : " " + origin + " → " + destination;
    }

    private static String delay(LocalDateTime scheduled, LocalDateTime expected) {
        if (scheduled == null || expected == null) {
            return "";
        }
        long minutes = Duration.between(scheduled, expected).toMinutes();
        long h = minutes / 60;
        long m = minutes % 60;
        return " by " + (h == 0 ? m + " min" : m == 0 ? h + " h" : h + " h " + m + " min");
    }

    static String time(LocalDateTime utc, String zone) {
        return utc == null ? "—" : local(utc, zone).format(TIME);
    }

    static String day(LocalDateTime utc, String zone) {
        return utc == null ? "—" : local(utc, zone).format(DAY);
    }

    private static LocalDateTime local(LocalDateTime utc, String zone) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zoneOf(zone)).toLocalDateTime();
    }

    private static ZoneId zoneOf(String zone) {
        try {
            return zone == null ? ZoneOffset.UTC : ZoneId.of(zone);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    private static String nullToDash(String value) {
        return value == null ? "your flight" : value;
    }
}
