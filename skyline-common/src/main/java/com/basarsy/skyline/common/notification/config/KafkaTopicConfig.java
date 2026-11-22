package com.basarsy.skyline.common.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /** Cancellations, consumed by the reservation service to cancel the flight's bookings. */
    public static final String FLIGHT_CANCELLED_TOPIC = "skyline.flight.cancelled";

    /**
     * Every change a passenger should hear about (status, delay, gate, reschedule, cancellation), as one event
     * type with a {@code type} field. A separate topic from cancellations on purpose: a consumer of that topic
     * cancels bookings for any record that carries a flight id.
     */
    public static final String FLIGHT_CHANGED_TOPIC = "skyline.flight.changed";

    /** Dead-letter topic: records a consumer could not process after its retries (Spring Kafka's ".DLT" convention). */
    public static final String DEAD_LETTER_SUFFIX = ".DLT";
    public static final String FLIGHT_CANCELLED_DLT_TOPIC = FLIGHT_CANCELLED_TOPIC + DEAD_LETTER_SUFFIX;
    public static final String FLIGHT_CHANGED_DLT_TOPIC = FLIGHT_CHANGED_TOPIC + DEAD_LETTER_SUFFIX;

    @Value("${spring.kafka.topic.flight-cancellation.partitions:3}")
    private int partitions;

    @Value("${spring.kafka.topic.flight-cancellation.replicas:1}")
    private int replicas;

    @Bean
    public NewTopic flightCancelledTopic() {
        return topic(FLIGHT_CANCELLED_TOPIC);
    }

    /** Same partition count as the source topic, so a dead letter can keep its original partition. */
    @Bean
    public NewTopic flightCancelledDeadLetterTopic() {
        return topic(FLIGHT_CANCELLED_DLT_TOPIC);
    }

    @Bean
    public NewTopic flightChangedTopic() {
        return topic(FLIGHT_CHANGED_TOPIC);
    }

    @Bean
    public NewTopic flightChangedDeadLetterTopic() {
        return topic(FLIGHT_CHANGED_DLT_TOPIC);
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
