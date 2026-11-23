package com.basarsy.skyline.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** skyline-common.yml's Feign timeouts bind to OpenFeign's own properties (a misspelt key is silently ignored). */
class FeignTimeoutsTest {

    @Test
    void everyFeignClientGetsShortTimeouts() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("skyline-common", new ClassPathResource("skyline-common.yml"))
                .forEach(environment.getPropertySources()::addLast);

        FeignClientProperties properties = Binder.get(environment)
                .bind("spring.cloud.openfeign.client", FeignClientProperties.class).get();

        FeignClientProperties.FeignClientConfiguration defaults = properties.getConfig().get("default");
        assertThat(defaults.getConnectTimeout()).isEqualTo(2000);
        assertThat(defaults.getReadTimeout()).isEqualTo(5000);
    }
}
