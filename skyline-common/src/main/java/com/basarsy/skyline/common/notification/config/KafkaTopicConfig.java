package com.basarsy.skyline.common.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String FLIGHT_CANCELLED_TOPIC = "skyline.flight.cancelled";

    /** Dead-letter topic: records a consumer could not process after its retries (Spring Kafka's ".DLT" convention). */
    public static final String DEAD_LETTER_SUFFIX = ".DLT";
    public static final String FLIGHT_CANCELLED_DLT_TOPIC = FLIGHT_CANCELLED_TOPIC + DEAD_LETTER_SUFFIX;

    @Value("${spring.kafka.topic.flight-cancellation.partitions:3}")
    private int partitions;

    @Value("${spring.kafka.topic.flight-cancellation.replicas:1}")
    private int replicas;

    @Bean
    public NewTopic flightCancelledTopic() {
        return TopicBuilder.name(FLIGHT_CANCELLED_TOPIC)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    /** Same partition count as the source topic, so a dead letter can keep its original partition. */
    @Bean
    public NewTopic flightCancelledDeadLetterTopic() {
        return TopicBuilder.name(FLIGHT_CANCELLED_DLT_TOPIC)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
