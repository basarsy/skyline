package com.basarsy.skyline.common.outbox;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Carries a trace across the outbox. An event is sent later, by the publisher's own thread, so without this its
 * Kafka record would start a new trace and a booking could not be followed to the notification it causes. The
 * W3C {@code traceparent} of the span that records the event is stored with it, and the publisher sends it inside
 * an observation that continues that trace: the producer's observation takes the current observation as its parent
 * (not the scheduler's), and puts the trace on the record for the consumer.
 */
public class OutboxTracing {

    /** No tracing: nothing is stored and events are sent as they are. */
    public static final OutboxTracing NONE = new OutboxTracing(null, null, ObservationRegistry.NOOP);

    private static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;
    private final ObservationRegistry observations;

    public OutboxTracing(Tracer tracer, Propagator propagator, ObservationRegistry observations) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.observations = observations;
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

    /**
     * Runs {@code send} in an observation continuing the trace {@code traceParent} (as is when there is none).
     * A receiving context: its span is taken from the stored trace, whatever observation (the scheduled run) is
     * current, and observations started inside it (the Kafka producer's) are its children.
     */
    <T> T continuing(String traceParent, Supplier<T> send) {
        if (traceParent == null || observations.isNoop()) {
            return send.get();
        }
        ReceiverContext<Map<String, String>> context = new ReceiverContext<>(Map::get);
        context.setCarrier(Map.of(TRACEPARENT, traceParent));
        context.setRemoteServiceName("outbox");
        Observation publish = Observation.createNotStarted("outbox.publish", () -> context, observations).start();
        try (Observation.Scope ignored = publish.openScope()) {
            return send.get();
        } finally {
            publish.stop();
        }
    }
}
