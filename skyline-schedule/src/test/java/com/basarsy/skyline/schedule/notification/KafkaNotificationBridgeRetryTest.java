package com.basarsy.skyline.schedule.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.notification.config.KafkaTopicConfig;
import com.basarsy.skyline.schedule.event.FlightCancelledEvent;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaNotificationBridgeRetryTest {

    @SuppressWarnings("unchecked")
    final KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
    final KafkaNotificationBridge bridge = new KafkaNotificationBridge(template, Duration.ZERO);
    final FlightCancelledEvent event = new FlightCancelledEvent(UUID.randomUUID());

    @Test
    void aFailedSendIsRetriedUntilItSucceeds() {
        when(template.send(anyString(), anyString(), any()))
                .thenReturn(failed(), failed(), CompletableFuture.completedFuture(mock(SendResult.class)));

        bridge.handleFlightCancelled(event);

        verify(template, times(3)).send(KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC, event.flightId().toString(), event);
    }

    @Test
    void retriesAreBoundedAndNeverThrowAfterCommit() {
        when(template.send(anyString(), anyString(), any())).thenReturn(failed());

        assertThatCode(() -> bridge.handleFlightCancelled(event)).doesNotThrowAnyException();

        verify(template, times(KafkaNotificationBridge.MAX_ATTEMPTS))
                .send(eq(KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC), anyString(), any());
    }

    private static CompletableFuture<SendResult<String, Object>> failed() {
        return CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
    }
}
