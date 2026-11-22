package com.basarsy.skyline.reservation.event;

/** What a {@link BookingEvent} reports. */
public enum BookingEventType {
    /** A booking (one or more reservations) was confirmed. */
    BOOKED,
    /** The passenger (or staff on their behalf) cancelled a reservation. Airline cancellations are flight events. */
    CANCELLED
}
