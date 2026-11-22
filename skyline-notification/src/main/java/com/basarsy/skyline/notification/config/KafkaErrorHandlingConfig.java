package com.basarsy.skyline.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * A record that fails is retried a few times, then parked on {@code <topic>.DLT} (same partition) instead of
 * being retried forever or skipped silently. Values are plain text here, so Boot's String KafkaTemplate
 * republishes them unchanged. Spring Boot wires this error handler into the listener container factory.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    static final long RETRY_INTERVAL_MS = 1_000;
    static final long MAX_RETRIES = 3;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    }
}
