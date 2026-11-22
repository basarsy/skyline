package com.basarsy.skyline.notification.service;

/**
 * What to tell someone: a short in-app entry ({@code title}, {@code summary}) and an email ({@code title} as the
 * subject, {@code emailBody} as plain text). {@code path} is the page of the web app it is about.
 *
 * @param kind  stable code for the web app (icons, filters), e.g. {@code FLIGHT_DELAYED}
 * @param email false for changes that only belong in the inbox (boarding has started)
 */
public record Message(String kind, String title, String summary, String emailBody, String path, boolean email) {}
