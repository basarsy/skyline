package com.basarsy.skyline.schedule.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionOperations;

class OutboxPublisherTest {

    static final OutboxProperties PROPERTIES = new OutboxProperties(
            10, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(1), 3, Duration.ofDays(7));

    final OutboxRepository repository = mock(OutboxRepository.class);
    @SuppressWarnings("unchecked")
    final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    final OutboxPublisher publisher =
            new OutboxPublisher(repository, kafka, TransactionOperations.withoutTransaction(), PROPERTIES);

    @Test
    void sendsTheWholeBatchKeyedByAggregateAndMarksItPublished() {
        OutboxEvent first = event(0);
        OutboxEvent second = event(0);
        when(repository.claimBatch(10)).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(acked());

        assertThat(publisher.publishPending()).isEqualTo(2);

        verify(kafka).send(first.topic(), first.aggregateId().toString(), first.payload());
        verify(kafka).send(second.topic(), second.aggregateId().toString(), second.payload());
        verify(repository).markPublished(List.of(first.id(), second.id()));
        verify(repository, never()).markFailed(any(), anyInt(), anyString(), any());
    }

    @Test
    void aFailedSendIsRecordedAndEndsTheRunLeavingTheRestForLater() {
        OutboxEvent sent = event(0);
        OutboxEvent failing = event(0);
        OutboxEvent untried = event(0);
        when(repository.claimBatch(10)).thenReturn(List.of(sent, failing, untried));
        when(kafka.send(anyString(), eq(sent.aggregateId().toString()), anyString())).thenReturn(acked());
        when(kafka.send(anyString(), eq(failing.aggregateId().toString()), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker unavailable")));

        assertThat(publisher.publishPending()).isEqualTo(1);

        verify(repository).markPublished(List.of(sent.id()));
        verify(repository).markFailed(
                eq(failing.id()), eq(1), contains("broker unavailable"), eq(Duration.ofSeconds(1)));
        verify(kafka, never()).send(anyString(), eq(untried.aggregateId().toString()), anyString());
    }

    @Test
    void aSendThatThrowsSynchronouslyIsRecordedToo() {
        OutboxEvent event = event(0);
        when(repository.claimBatch(10)).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new KafkaException("metadata timeout"));

        assertThat(publisher.publishPending()).isZero();

        verify(repository).markFailed(eq(event.id()), eq(1), contains("metadata timeout"), eq(Duration.ofSeconds(1)));
        verify(repository).markPublished(List.of());
    }

    @Test
    void anUnackedSendTimesOutAndIsRetriedLater() {
        OutboxEvent event = event(0);
        when(repository.claimBatch(10)).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        assertThat(publisher.publishPending()).isZero();

        verify(repository).markFailed(eq(event.id()), eq(1), contains("Timeout"), any());
    }

    @Test
    void aRowThatFailedBeforeIsSentOnALaterRun() {
        OutboxEvent event = event(0);
        when(repository.claimBatch(10)).thenReturn(List.of(event), List.of(event));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("down")), acked());

        assertThat(publisher.publishPending()).isZero();
        assertThat(publisher.publishPending()).isEqualTo(1);

        verify(repository).markPublished(List.of(event.id()));
    }

    @Test
    void repeatedFailuresBackOffExponentiallyUpToTheCap() {
        OutboxEvent event = event(4);
        when(repository.claimBatch(10)).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("down")));

        publisher.publishPending();

        // Fifth failure: 1s * 2^4.
        verify(repository).markFailed(eq(event.id()), eq(5), anyString(), eq(Duration.ofSeconds(16)));
        assertThat(PROPERTIES.backoffAfter(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(PROPERTIES.backoffAfter(7)).isEqualTo(Duration.ofMinutes(1));
        assertThat(PROPERTIES.backoffAfter(10_000)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void anEmptyOutboxSendsNothing() {
        when(repository.claimBatch(10)).thenReturn(List.of());

        assertThat(publisher.publishPending()).isZero();

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void cleanupDeletesPublishedRowsPastTheRetention() {
        when(repository.deletePublishedBefore(Duration.ofDays(7))).thenReturn(5);

        assertThat(publisher.deletePublished()).isEqualTo(5);
    }

    private static OutboxEvent event(int attempts) {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "skyline.flight.cancelled",
                "{\"flightId\":\"" + UUID.randomUUID() + "\"}", attempts);
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<SendResult<String, String>> acked() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }
}
