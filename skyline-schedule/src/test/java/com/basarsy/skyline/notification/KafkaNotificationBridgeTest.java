package com.basarsy.skyline.notification;

import com.basarsy.skyline.common.notification.config.KafkaTopicConfig;
import com.basarsy.skyline.schedule.event.FlightCancelledEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class KafkaNotificationBridgeTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void shouldBridgeFlightCancelledEventToKafka() throws InterruptedException {
        // Setup Kafka Consumer to verify message receipt
        BlockingQueue<ConsumerRecord<String, FlightCancelledEvent>> records = new LinkedBlockingQueue<>();
        
        JsonDeserializer<FlightCancelledEvent> payloadDeserializer = new JsonDeserializer<>(FlightCancelledEvent.class);
        payloadDeserializer.addTrustedPackages("com.basarsy.skyline.*");

        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        DefaultKafkaConsumerFactory<String, FlightCancelledEvent> consumerFactory = 
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), payloadDeserializer);

        ContainerProperties containerProperties = new ContainerProperties(KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC);
        KafkaMessageListenerContainer<String, FlightCancelledEvent> container = 
                new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        
        container.setupMessageListener((MessageListener<String, FlightCancelledEvent>) records::add);
        container.start();

        try {
            // Trigger local Spring Event
            UUID flightId = UUID.randomUUID();
            FlightCancelledEvent event = new FlightCancelledEvent(flightId);
            eventPublisher.publishEvent(event);

            // Verify message appears in Kafka
            ConsumerRecord<String, FlightCancelledEvent> received = records.poll(10, TimeUnit.SECONDS);
            
            assertThat(received).isNotNull();
            assertThat(received.key()).isEqualTo(flightId.toString());
            assertThat(received.value().flightId()).isEqualTo(flightId);
        } finally {
            container.stop();
        }
    }
}
