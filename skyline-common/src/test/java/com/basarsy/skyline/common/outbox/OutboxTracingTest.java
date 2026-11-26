package com.basarsy.skyline.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The trace of the request that records an event is the one its Kafka record is sent in, also when the publisher
 * runs inside an observation of its own (Spring observes every @Scheduled run).
 */
class OutboxTracingTest {

    private final io.opentelemetry.api.trace.Tracer otel = SdkTracerProvider.builder().build().get("test");
    private final OtelCurrentTraceContext context = new OtelCurrentTraceContext();
    private final Tracer tracer = new OtelTracer(otel, context, event -> { },
            new OtelBaggageManager(context, List.of(), List.of()));
    private final Propagator propagator =
            new OtelPropagator(ContextPropagators.create(W3CTraceContextPropagator.getInstance()), otel);
    /** Wired like Spring Boot's: sender, receiver and default tracing handlers, the first that applies. */
    private final ObservationRegistry observations = registry();
    private final OutboxTracing tracing = new OutboxTracing(tracer, propagator, observations);

    @Test
    void anEventIsSentInTheTraceOfTheRequestThatRecordedItNotTheSchedulers() {
        Span request = tracer.nextSpan().name("POST /reservations").start();
        String traceParent;
        try (Tracer.SpanInScope ignored = tracer.withSpan(request)) {
            traceParent = tracing.currentTraceParent();
        } finally {
            request.end();
        }
        assertThat(traceParent).isEqualTo(
                "00-" + request.context().traceId() + "-" + request.context().spanId() + "-01");

        // Later: the scheduled publisher run is an observation of its own, and the Kafka producer starts its
        // observation with whatever is current as its parent.
        Observation scheduledRun = Observation.start("tasks.scheduled.execution", observations);
        Span producer;
        try (Observation.Scope ignored = scheduledRun.openScope()) {
            producer = tracing.continuing(traceParent, () -> {
                Observation send = Observation.start("spring.kafka.template", observations);
                Span span = send.getContext().<TracingObservationHandler.TracingContext>get(
                        TracingObservationHandler.TracingContext.class).getSpan();
                send.stop();
                return span;
            });
        } finally {
            scheduledRun.stop();
        }

        assertThat(producer.context().traceId()).isEqualTo(request.context().traceId());
    }

    @Test
    void withoutATraceNothingIsStoredAndEventsAreSentAsTheyAre() {
        assertThat(tracing.currentTraceParent()).isNull();
        assertThat(tracing.continuing(null, () -> "sent")).isEqualTo("sent");
        assertThat(OutboxTracing.NONE.currentTraceParent()).isNull();
        assertThat(OutboxTracing.NONE.continuing("00-x-y-01", () -> "sent")).isEqualTo("sent");
    }

    private ObservationRegistry registry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(
                new io.micrometer.observation.ObservationHandler.FirstMatchingCompositeObservationHandler(
                        new PropagatingSenderTracingObservationHandler<>(tracer, propagator),
                        new PropagatingReceiverTracingObservationHandler<>(tracer, propagator),
                        new DefaultTracingObservationHandler(tracer)));
        return registry;
    }
}
