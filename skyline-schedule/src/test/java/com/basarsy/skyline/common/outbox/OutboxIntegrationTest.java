package com.basarsy.skyline.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.notification.config.KafkaTopicConfig;
import com.basarsy.skyline.schedule.client.FleetClient;
import com.basarsy.skyline.schedule.client.dto.AircraftResponse;
import com.basarsy.skyline.schedule.client.dto.AircraftStatus;
import com.basarsy.skyline.schedule.client.dto.AircraftTypeSummary;
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.event.FlightCancelledEvent;
import com.basarsy.skyline.schedule.repository.FlightRepository;
import com.basarsy.skyline.schedule.service.FlightService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** The outbox end to end against real PostgreSQL and Kafka: cancel a flight, publish, consume. */
@SpringBootTest(properties = {
        // Fail fast while the broker is paused, so the outage test doesn't wait for the production timeouts.
        "spring.kafka.producer.properties.max.block.ms=2000",
        "spring.kafka.producer.properties.request.timeout.ms=1000",
        "spring.kafka.producer.properties.delivery.timeout.ms=3000",
        "skyline.outbox.send-timeout=PT5S",
        "skyline.outbox.retry-backoff=PT0.2S",
        "skyline.outbox.max-retry-backoff=PT0.2S",
        "skyline.outbox.cleanup-batch-size=2"})
@ActiveProfiles("test")
@Testcontainers
class OutboxIntegrationTest {

    /** Whether the only outbox row is published. */
    /** A cancellation also records a FlightChangedEvent; these tests follow the cancelled topic's row. */
    static final String CANCELLED_ROW = "FROM outbox_events WHERE topic = '" + KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC + "'";
    /** Every row sent: the cancelled event and, after it (same flight, so in order), the change event. */
    static final String PUBLISHED = "SELECT bool_and(published_at IS NOT NULL) FROM outbox_events";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @MockitoBean
    FleetClient fleetClient;

    @Autowired
    FlightService flightService;
    @Autowired
    FlightRepository flightRepository;
    @Autowired
    Outbox outbox;
    @Autowired
    OutboxRepository outboxRepository;
    @Autowired
    OutboxPublisher publisher;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM outbox_events");
        when(fleetClient.getAircraft(any())).thenAnswer(inv -> new AircraftResponse(inv.getArgument(0), "TC-JFA",
                new AircraftTypeSummary(UUID.randomUUID(), "Airbus", "A320neo", 180), AircraftStatus.ACTIVE, 2020));
    }

    @Test
    void cancellingAFlightWritesTheEventInTheSameTransaction() {
        Flight flight = scheduledFlight();

        flightService.cancelFlight(flight.getId());

        Map<String, Object> row = jdbc.queryForMap("SELECT * " + CANCELLED_ROW);
        assertThat(row.get("aggregate_id")).isEqualTo(flight.getId());
        assertThat(row.get("event_type")).isEqualTo("FlightCancelledEvent");
        assertThat(row.get("topic")).isEqualTo(KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC);
        assertThat(row.get("payload")).isEqualTo("{\"flightId\":\"" + flight.getId() + "\"}");
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("attempts")).isEqualTo(0);

        Map<String, Object> change = jdbc.queryForMap("SELECT * FROM outbox_events WHERE topic = ?",
                KafkaTopicConfig.FLIGHT_CHANGED_TOPIC);
        assertThat(change.get("aggregate_id")).isEqualTo(flight.getId());
        assertThat(change.get("event_type")).isEqualTo("FlightChangedEvent");
        assertThat((String) change.get("payload")).contains("\"type\":\"CANCELLED\"", "\"previousStatus\":\"SCHEDULED\"");
    }

    @Test
    void aRolledBackCancellationLeavesNoEvent() {
        Flight flight = scheduledFlight();

        transactions.executeWithoutResult(status -> {
            flightService.cancelFlight(flight.getId());
            status.setRollbackOnly();
        });

        assertThat(flightRepository.findById(flight.getId()).orElseThrow().getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
    }

    @Test
    void anEventCannotBeWrittenOutsideATransaction() {
        assertThatThrownBy(() -> outbox.append(KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC, UUID.randomUUID(),
                new FlightCancelledEvent(UUID.randomUUID())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void thePublisherDeliversTheEventWithTheSameWireFormatAsBefore() {
        Flight flight = scheduledFlight();
        flightService.cancelFlight(flight.getId());

        // Wait for delivery instead of assuming the first attempt wins (see publishUntil).
        publishUntil("published", () -> jdbc.queryForObject(PUBLISHED, Boolean.class));

        ConsumerRecord<String, String> record = consume(flight.getId());
        assertThat(record.value()).isEqualTo("{\"flightId\":\"" + flight.getId() + "\"}");
        assertThat(record.headers().lastHeader("__TypeId__")).as("no type headers").isNull();
        assertThat(publisher.publishPending()).as("nothing left to send").isZero();
    }

    @Test
    void whileKafkaIsDownTheEventWaitsAndIsDeliveredOnceItIsBack() {
        Flight flight = scheduledFlight();
        flightService.cancelFlight(flight.getId());

        var docker = DockerClientFactory.instance().client();
        docker.pauseContainerCmd(kafka.getContainerId()).exec();
        Instant claimed;
        Instant failedAt;
        try {
            claimed = databaseNow();
            assertThat(publisher.publishPending()).isZero();
            failedAt = databaseNow();
        } finally {
            docker.unpauseContainerCmd(kafka.getContainerId()).exec();
        }

        Map<String, Object> failed = jdbc.queryForMap("SELECT * " + CANCELLED_ROW);
        assertThat(failed.get("published_at")).isNull();
        assertThat(failed.get("attempts")).isEqualTo(1);
        assertThat(failed.get("last_error")).isNotNull();
        // The send blocked for seconds, far longer than the 0.2s backoff. The backoff counts from the failure,
        // so the row is still not due once the run is over (counted from the claim, it would be overdue).
        assertThat(Duration.between(claimed, failedAt)).isGreaterThan(Duration.ofSeconds(1));
        assertThat(jdbc.queryForObject("SELECT next_attempt_at " + CANCELLED_ROW, OffsetDateTime.class).toInstant())
                .isAfter(failedAt);

        publishUntil("delivered after Kafka is back", () -> jdbc.queryForObject(PUBLISHED, Boolean.class));
        assertThat(consume(flight.getId()).value()).contains(flight.getId().toString());
    }

    @Test
    void concurrentPublishersClaimDisjointRows() throws Exception {
        transactions.executeWithoutResult(status -> {
            outboxRepository.insert(UUID.randomUUID(), "Test", "t", "{}", null);
            outboxRepository.insert(UUID.randomUUID(), "Test", "t", "{}", null);
        });
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<List<OutboxEvent>> first = CompletableFuture.supplyAsync(() -> transactions.execute(status -> {
            List<OutboxEvent> batch = outboxRepository.claimBatch(1);
            claimed.countDown();
            await(release);
            return batch;
        }));
        try {
            assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();
            List<OutboxEvent> second = transactions.execute(status -> outboxRepository.claimBatch(10));

            assertThat(second).hasSize(1);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).hasSize(1)
                    .extracting(OutboxEvent::id).doesNotContain(second.get(0).id());
        } finally {
            release.countDown();
        }
    }

    @Test
    void aLaterEventIsNotSentWhileAnOlderOneOfTheSameAggregateIsFailing() {
        UUID aggregate = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            // Not a valid topic name, so Kafka rejects the send.
            outboxRepository.insert(aggregate, "Test", "not a valid topic!", "{\"n\":1}", null);
            outboxRepository.insert(aggregate, "Test", KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC, "{\"n\":2}", null);
            outboxRepository.insert(other, "Test", KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC, "{\"n\":3}", null);
        });

        // Only the head of each aggregate is claimable.
        List<OutboxEvent> claimable = transactions.execute(status -> outboxRepository.claimBatch(10));
        assertThat(claimable).extracting(OutboxEvent::payload).containsExactlyInAnyOrder("{\"n\":1}", "{\"n\":3}");

        publishUntil("other aggregate published", () -> row("{\"n\":3}").get("published_at") != null);
        // Retry the failing head past several backoffs: the second event stays behind it, unsent.
        publishUntil("head retried", () -> (Integer) row("{\"n\":1}").get("attempts") >= 3);
        assertThat(row("{\"n\":1}").get("published_at")).isNull();
        Map<String, Object> next = row("{\"n\":2}");
        assertThat(next.get("published_at")).isNull();
        assertThat(next.get("attempts")).as("never tried").isEqualTo(0);

        // Once the head is out of the way, the next one goes.
        jdbc.update("UPDATE outbox_events SET published_at = clock_timestamp() WHERE payload = '{\"n\":1}'");
        publishUntil("second event published", () -> row("{\"n\":2}").get("published_at") != null);
    }

    @Test
    void cleanupDeletesOnlyExpiredPublishedRowsInChunks() {
        transactions.executeWithoutResult(status -> {
            for (int i = 0; i < 5; i++) {
                outboxRepository.insert(UUID.randomUUID(), "Test", "t", "{\"expired\":true}", null);
            }
            outboxRepository.insert(UUID.randomUUID(), "Test", "t", "{\"recent\":true}", null);
            outboxRepository.insert(UUID.randomUUID(), "Test", "t", "{\"pending\":true}", null);
        });
        jdbc.update("UPDATE outbox_events SET published_at = now() - INTERVAL '8 days' WHERE payload LIKE '%expired%'");
        jdbc.update("UPDATE outbox_events SET published_at = now() WHERE payload LIKE '%recent%'");

        // Chunks of 2 (see the test properties): 2 + 2 + 1.
        assertThat(publisher.deletePublished()).isEqualTo(5);

        assertThat(jdbc.queryForList("SELECT payload FROM outbox_events", String.class))
                .containsExactlyInAnyOrder("{\"recent\":true}", "{\"pending\":true}");
    }

    private Instant databaseNow() {
        return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
    }

    private Map<String, Object> row(String payload) {
        return jdbc.queryForMap("SELECT * FROM outbox_events WHERE payload = ?", payload);
    }

    /**
     * Runs the publisher until {@code done}. A first send to a fresh topic (or right after the broker was paused
     * by another test) can outlast the producer's blocking timeout; the row is then retried with backoff.
     */
    private void publishUntil(String alias, Callable<Boolean> done) {
        Awaitility.await(alias)
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(300))
                .until(() -> {
                    publisher.publishPending();
                    return done.call();
                });
    }

    private Flight scheduledFlight() {
        Flight flight = new Flight();
        flight.setFlightNumber("SK" + (int) (Math.random() * 9000 + 1000));
        flight.setRouteId(UUID.randomUUID());
        flight.setAircraftId(UUID.randomUUID());
        flight.setDepartureTime(LocalDateTime.now().plusDays(10));
        flight.setArrivalTime(LocalDateTime.now().plusDays(10).plusHours(4));
        flight.setStatus(FlightStatus.SCHEDULED);
        flight.setAvailableSeats(179);
        flight.setBasePrice(new BigDecimal("100.00"));
        return flightRepository.saveAndFlush(flight);
    }

    /** Reads the topic from the start until the record keyed by {@code flightId} turns up. */
    private static ConsumerRecord<String, String> consume(UUID flightId) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.key().equals(flightId.toString())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No record for flight " + flightId + " on " + KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
