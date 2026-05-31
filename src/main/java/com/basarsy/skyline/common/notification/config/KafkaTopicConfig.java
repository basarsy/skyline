package com.basarsy.skyline.common.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String FLIGHT_CANCELLED_TOPIC = "skyline.flights.cancellation";

    @Bean
    public NewTopic flightCancelledTopic() {
        return TopicBuilder.name(FLIGHT_CANCELLED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
