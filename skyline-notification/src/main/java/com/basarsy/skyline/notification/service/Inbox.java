package com.basarsy.skyline.notification.service;

import com.basarsy.skyline.notification.config.NotificationProperties;
import com.basarsy.skyline.notification.repository.EmailOutboxRepository;
import com.basarsy.skyline.notification.repository.NotificationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
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
            emails.enqueue(address.trim(), message.title(), body, emailKey(dedupeKey, address));
        }
    }

    /**
     * The address goes into the key as a SHA-256 digest, not as text: an address can be up to 254 characters, and
     * spelled out it would push the key past the column's 255, failing the whole transaction it is written in.
     */
    static String emailKey(String dedupeKey, String address) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalised(address).getBytes(StandardCharsets.UTF_8));
            return dedupeKey + ":email:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    /** One address however it was typed: what emails are grouped and deduplicated by. */
    static String normalised(String address) {
        return address.trim().toLowerCase(Locale.ROOT);
    }
}
