package com.basarsy.skyline.notification.service;

import com.basarsy.skyline.notification.config.NotificationProperties;
import com.basarsy.skyline.notification.repository.EmailOutboxRepository;
import com.basarsy.skyline.notification.repository.NotificationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers a {@link Message}: an in-app entry for the user and a queued email for the address. Both are keyed by
 * {@code dedupeKey} plus the recipient, so handling the same event twice delivers nothing new.
 */
@Component
@RequiredArgsConstructor
public class Inbox {

    private final NotificationRepository notifications;
    private final EmailOutboxRepository emails;
    private final NotificationProperties properties;

    @Transactional(propagation = Propagation.MANDATORY)
    public void toUser(UUID userId, Message message, String dedupeKey) {
        if (userId != null) {
            notifications.insert(userId, message.kind(), message.title(), message.summary(), message.path(),
                    dedupeKey + ":user:" + userId);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void toEmail(String address, Message message, String dedupeKey) {
        if (address != null && !address.isBlank() && message.email()) {
            String body = message.emailBody()
                    + (message.path() == null ? "" : "\n\n" + properties.link(message.path()))
                    + "\n\nSkyline";
            emails.enqueue(address.trim(), message.title(), body, dedupeKey + ":email:" + address.trim().toLowerCase());
        }
    }
}
