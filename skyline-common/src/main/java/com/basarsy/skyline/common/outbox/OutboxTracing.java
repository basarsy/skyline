package com.basarsy.skyline.common.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Carries a trace across the outbox. An event is sent later, by the publisher's own thread, so without this its
 * Kafka record would start a new trace and a booking could not be followed to the notification it causes. The
 * W3C {@code traceparent} of the span that records the event is stored with it, and the publisher sends it
 * inside a span continuing that trace (the producer's observation puts it on the record for the consumer).
 */
public class OutboxTracing {

    /** No tracing: nothing is stored and events are sent as they are. */
    public static final OutboxTracing NONE = new OutboxTracing(null, null);

    private static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public OutboxTracing(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** The current span's {@code traceparent}, or null outside a trace. */
    String currentTraceParent() {
        if (tracer == null || propagator == null) {
            return null;
        }
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(span.context(), carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    /** Runs {@code send} in a span continuing the trace {@code traceParent} (as is when there is none). */
    <T> T continuing(String traceParent, Supplier<T> send) {
        if (tracer == null || propagator == null || traceParent == null) {
            return send.get();
        }
        Span span = propagator.extract(Map.of(TRACEPARENT, traceParent), Map::get).name("outbox publish").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return send.get();
        } finally {
            span.end();
        }
    }
}
