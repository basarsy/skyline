package com.basarsy.skyline.notification.config;

import com.basarsy.skyline.common.kafka.ConsumerRetryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * A record that fails is retried with growing waits for minutes (see {@link ConsumerRetryProperties}), then
 * parked on {@code <topic>.DLT} (same partition) instead of being retried forever or skipped silently; malformed
 * JSON is parked at once. Values are plain text here, so Boot's String KafkaTemplate republishes them unchanged.
 * Spring Boot wires this error handler into the listener container factory.
 */
@Configuration
@EnableConfigurationProperties(ConsumerRetryProperties.class)
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate, ConsumerRetryProperties retry) {
        return retry.errorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate));
    }
}
