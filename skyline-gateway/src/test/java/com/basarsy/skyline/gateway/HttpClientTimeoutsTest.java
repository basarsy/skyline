package com.basarsy.skyline.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.HttpClientProperties;

/** The gateway gives up on a hung service instead of holding the connection (a misspelt key is ignored). */
@SpringBootTest
class HttpClientTimeoutsTest {

    @Autowired
    private HttpClientProperties httpClient;

    @Test
    void downstreamCallsHaveConnectAndResponseTimeouts() {
        assertThat(httpClient.getConnectTimeout()).isEqualTo(2000);
        assertThat(httpClient.getResponseTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
