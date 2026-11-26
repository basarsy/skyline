package com.basarsy.skyline.reservation.config;

import com.basarsy.skyline.common.kafka.ConsumerRetryProperties;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Consumer error handling: a failing record is retried with growing waits for minutes (see
 * {@link ConsumerRetryProperties}), then parked on its dead-letter topic ({@code <topic>.DLT}, same partition;
 * see {@code KafkaTopicConfig}) instead of being retried forever or skipped silently. Records that can't be
 * deserialized are not retryable and go to the DLT directly. Spring Boot wires this {@code CommonErrorHandler}
 * bean into the auto-configured listener container factory.
 */
@Configuration
@EnableConfigurationProperties(ConsumerRetryProperties.class)
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> deadLetterKafkaTemplate,
            ConsumerRetryProperties retry) {
        return retry.errorHandler(new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate));
    }

    /**
     * This service only consumes, so it has no producer configured. The recoverer republishes either the
     * deserialized value (an event object, written as JSON) or, for a record that failed deserialization,
     * the original raw bytes, so the value serializer handles both.
     */
    @Bean
    public KafkaTemplate<Object, Object> deadLetterKafkaTemplate(KafkaProperties properties,
            ObjectProvider<SslBundles> sslBundles) {
        Map<Class<?>, Serializer<?>> byType = new LinkedHashMap<>(); // order matters: Object goes last
        byType.put(byte[].class, new ByteArraySerializer());
        byType.put(String.class, new StringSerializer());
        byType.put(Object.class, new JsonSerializer<>());

        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(sslBundles.getIfAvailable()));
        // Consumers bind to their own DTOs: don't leak this service's class names into headers.
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        KafkaTemplate<Object, Object> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config,
                new DelegatingByTypeSerializer(byType, true), new DelegatingByTypeSerializer(byType, true)));
        // A parked record keeps the trace of the record that failed (only Boot's own template is observed by
        // spring.kafka.template.observation-enabled).
        template.setObservationEnabled(true);
        return template;
    }
}
