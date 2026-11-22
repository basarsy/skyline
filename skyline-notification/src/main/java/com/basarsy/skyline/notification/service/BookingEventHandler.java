package com.basarsy.skyline.notification.service;

import com.basarsy.skyline.notification.event.BookingEvent;
import com.basarsy.skyline.notification.repository.RecipientRepository;
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

    @Transactional
    public void handle(BookingEvent event) {
        if (event.type() == null || event.bookingId() == null || event.reservations().isEmpty()) {
            log.warn("Ignoring a booking event without type, booking id or reservations: {}", event);
            return;
        }
        switch (event.type()) {
            case BookingEvent.BOOKED -> {
                event.reservations().forEach(item ->
                        recipients.booked(event.bookingId(), event.userId(), event.contactEmail(), item));
                Message message = Messages.booked(event, "/trips/" + event.reservations().get(0).reservationId());
                String key = "booking:" + event.bookingId() + ":BOOKED";
                inbox.toUser(event.userId(), message, key);
                inbox.toEmail(event.contactEmail(), message, key);
            }
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
}
