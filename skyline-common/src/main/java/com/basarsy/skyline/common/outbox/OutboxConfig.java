package com.basarsy.skyline.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The transactional outbox, for services that publish Kafka events: on only with {@code skyline.outbox.enabled=true}.
 * Every service scans this package, so the outbox classes are not components themselves; a service that turns it
 * on also needs the {@code outbox_events} table (the schedule service's {@code V3__outbox_events.sql} has it), the
 * {@code skyline.outbox.*} settings and a {@code KafkaTemplate<String, String>}.
 */
@Configuration
@ConditionalOnProperty(name = "skyline.outbox.enabled", havingValue = "true")
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {

    @Bean
    OutboxRepository outboxRepository(JdbcTemplate jdbc) {
        return new OutboxRepository(jdbc);
    }

    @Bean
    public Outbox outbox(OutboxRepository repository, ObjectMapper objectMapper) {
        return new Outbox(repository, objectMapper);
    }

    @Bean
    public OutboxPublisher outboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate,
            TransactionOperations transactions, OutboxProperties properties) {
        return new OutboxPublisher(repository, kafkaTemplate, transactions, properties);
    }

    /** Off with {@code skyline.outbox.scheduling-enabled=false} (tests drive the publisher by hand). */
    @Bean
    @ConditionalOnProperty(name = "skyline.outbox.scheduling-enabled", havingValue = "true", matchIfMissing = true)
    OutboxScheduler outboxScheduler(OutboxPublisher publisher) {
        return new OutboxScheduler(publisher);
    }
}
