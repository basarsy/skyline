package com.basarsy.skyline.notification.controller;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An in-app notification.
 *
 * @param kind      e.g. FLIGHT_DELAYED, FLIGHT_GATE, BOOKING_CONFIRMED, CHECK_IN_OPEN
 * @param link      path in the web app the notification is about, e.g. {@code /trips/<id>}
 * @param createdAt UTC
 */
public record NotificationResponse(UUID id, String kind, String title, String body, String link,
        LocalDateTime createdAt, boolean read) {}
