package com.basarsy.skyline.gateway;

import java.net.InetSocketAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /**
     * Key for the {@code RequestRateLimiter} routes: the client's IP address.
     *
     * <p>With {@code trusted-proxies = 0} that is the TCP peer. Behind N reverse proxies (the web container's nginx
     * is one) the peer is always the proxy, so the client address is read from the N-th last
     * {@code X-Forwarded-For} entry instead, the one the outermost trusted proxy appended. Only set it when every
     * request really passes through those proxies: anyone who can reach the gateway directly could otherwise pick
     * their own key.
     */
    @Bean
    KeyResolver clientIpKeyResolver(@Value("${skyline.rate-limit.trusted-proxies}") int trustedProxies) {
        RemoteAddressResolver resolver = trustedProxies > 0
                ? XForwardedRemoteAddressResolver.maxTrustedIndex(trustedProxies)
                : new RemoteAddressResolver() {};
        return exchange -> Mono.justOrEmpty(resolver.resolve(exchange)).map(RateLimitConfig::host);
    }

    private static String host(InetSocketAddress address) {
        return address.getAddress() != null ? address.getAddress().getHostAddress() : address.getHostString();
    }
}
