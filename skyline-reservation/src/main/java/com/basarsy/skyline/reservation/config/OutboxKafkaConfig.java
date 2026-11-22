package com.basarsy.skyline.reservation.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * The producer of the outbox publisher (booking events). Spring Boot's own KafkaTemplate backs off because
 * {@link KafkaErrorHandlingConfig} defines one for dead letters, so this one is explicit: String keys and values,
 * the JSON stored in {@code outbox_events} sent as is, plus the {@code spring.kafka.producer.*} settings.
 */
@Configuration
public class OutboxKafkaConfig {

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(KafkaProperties properties,
            ObjectProvider<SslBundles> sslBundles) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(sslBundles.getIfAvailable()));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }
}
