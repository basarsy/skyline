package com.basarsy.skyline.gateway;

import io.micrometer.tracing.handler.TracingObservationHandler;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Puts the request's trace id on every response as {@code X-Trace-Id}: the same trace the services downstream log
 * (the gateway sends it on as a W3C traceparent), so a failed request someone reports can be found in the logs
 * and traces of every service it went through. It is set just before the response is committed, from the
 * observation of the whole request, so error responses get it too.
 */
@Component
public class TraceIdResponseHeader implements WebFilter, Ordered {

    static final String HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            ServerRequestObservationContext.findCurrent(exchange.getAttributes())
                    .map(context -> context.<TracingObservationHandler.TracingContext>get(
                            TracingObservationHandler.TracingContext.class))
                    .map(TracingObservationHandler.TracingContext::getSpan)
                    .ifPresent(span -> exchange.getResponse().getHeaders().set(HEADER, span.context().traceId()));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
