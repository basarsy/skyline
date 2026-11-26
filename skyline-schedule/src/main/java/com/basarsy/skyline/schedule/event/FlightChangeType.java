package com.basarsy.skyline.schedule.event;

/** What a {@link FlightChangedEvent} reports. One operation can produce a gate change next to one of the others. */
public enum FlightChangeType {
    /** A status change no other type describes: boarding, departed, arrived, or delayed without a new time. */
    STATUS_CHANGED,
    /** A new or changed expected departure time (the flight is DELAYED). */
    DELAYED,
    /** The delay was lifted: the flight leaves at its scheduled time again. */
    DELAY_CLEARED,
    /** Ops moved the scheduled departure or arrival time. */
    RESCHEDULED,
    /** A gate was assigned or changed. */
    GATE_CHANGED,
    /** The airline cancelled the flight. */
    CANCELLED,
    /** The flight has a new flight number (previousFlightNumber is the old one). */
    FLIGHT_NUMBER_CHANGED
}
