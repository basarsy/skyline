package com.basarsy.skyline.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The trace of the request that records an event is the one its Kafka record is sent in. */
class OutboxTracingTest {

    private final io.opentelemetry.api.trace.Tracer otel = SdkTracerProvider.builder().build().get("test");
    private final OtelCurrentTraceContext context = new OtelCurrentTraceContext();
    private final Tracer tracer = new OtelTracer(otel, context, event -> { },
            new OtelBaggageManager(context, List.of(), List.of()));
    private final OutboxTracing tracing = new OutboxTracing(tracer,
            new OtelPropagator(ContextPropagators.create(W3CTraceContextPropagator.getInstance()), otel));

    @Test
    void anEventRecordedInARequestIsSentInThatRequestsTrace() {
        Span request = tracer.nextSpan().name("POST /reservations").start();
        String traceParent;
        try (Tracer.SpanInScope ignored = tracer.withSpan(request)) {
            traceParent = tracing.currentTraceParent();
        } finally {
            request.end();
        }
        assertThat(traceParent).isEqualTo(
                "00-" + request.context().traceId() + "-" + request.context().spanId() + "-01");

        // Later, on the publisher's thread with no trace of its own.
        Span sending = tracing.continuing(traceParent, tracer::currentSpan);

        assertThat(sending.context().traceId()).isEqualTo(request.context().traceId());
        assertThat(sending.context().parentId()).isEqualTo(request.context().spanId());
        assertThat(tracer.currentSpan()).as("nothing left in scope").isNull();
    }

    @Test
    void withoutATraceNothingIsStoredAndEventsAreSentAsTheyAre() {
        assertThat(tracing.currentTraceParent()).isNull();
        assertThat(tracing.continuing(null, tracer::currentSpan)).isNull();
        assertThat(OutboxTracing.NONE.currentTraceParent()).isNull();
    }
}
