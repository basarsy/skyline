package com.basarsy.skyline.common.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.BackOffExecution;

class ConsumerRetryPropertiesTest {

    private final ConsumerRetryProperties defaults = Binder.get(new StandardEnvironment())
            .bindOrCreate("skyline.kafka.consumer-retry", ConsumerRetryProperties.class);

    @Test
    void waitsGrowUpToAMinuteAndRetryingLastsAboutFiveMinutes() {
        BackOffExecution execution = defaults.backOff().start();
        List<Long> waits = new ArrayList<>();
        for (long wait = execution.nextBackOff(); wait != BackOffExecution.STOP; wait = execution.nextBackOff()) {
            waits.add(wait);
        }

        assertThat(waits).startsWith(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L);
        assertThat(waits).allSatisfy(wait -> assertThat(wait).isLessThanOrEqualTo(60_000L));
        assertThat(waits.stream().mapToLong(Long::longValue).sum()).isBetween(240_000L, 360_000L);
    }

    @Test
    void malformedJsonIsParkedAtOnceAndOtherFailuresAreRetried() {
        ConsumerRecordRecoverer deadLetter = mock(ConsumerRecordRecoverer.class);
        DefaultErrorHandler handler = defaults.errorHandler(deadLetter);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("skyline.booking.events", 0, 7, "k", "{not json");

        boolean parked = handler.handleOne(failure(new JsonParseException(null, "bad")), record,
                mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(parked).isTrue();
        verify(deadLetter).accept(any(), any());

        ConsumerRecordRecoverer untouched = mock(ConsumerRecordRecoverer.class);
        boolean parkedTransient = defaults.errorHandler(untouched).handleOne(
                failure(new IllegalStateException("db down")), record, mock(Consumer.class),
                mock(MessageListenerContainer.class));

        assertThat(parkedTransient).as("retried (seek back), not parked").isFalse();
        verify(untouched, never()).accept(any(), any());
    }

    private static ListenerExecutionFailedException failure(Exception cause) {
        return new ListenerExecutionFailedException("listener failed", cause);
    }
}
