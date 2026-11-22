package com.basarsy.skyline.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the notification service ({@code skyline.notification.*}).
 *
 * @param webBaseUrl          base of the links in emails (the web app)
 * @param mailFrom            sender of every email
 * @param emailBatchSize      pending emails sent per run
 * @param emailRetryBackoff   delay before retrying an email that failed, doubled on each further failure
 * @param emailMaxRetryBackoff upper bound of that delay
 */
@ConfigurationProperties("skyline.notification")
public record NotificationProperties(
        String webBaseUrl,
        String mailFrom,
        int emailBatchSize,
        Duration emailRetryBackoff,
        Duration emailMaxRetryBackoff) {

    /** Delay before the next attempt of an email that has now failed {@code attempts} (at least 1) times. */
    public Duration backoffAfter(int attempts) {
        Duration delay = emailRetryBackoff.multipliedBy(1L << Math.min(attempts - 1, 30));
        return delay.compareTo(emailMaxRetryBackoff) > 0 ? emailMaxRetryBackoff : delay;
    }

    /** An absolute link to a page of the web app, e.g. {@code /trips/<id>}. */
    public String link(String path) {
        return webBaseUrl.replaceAll("/+$", "") + path;
    }
}
