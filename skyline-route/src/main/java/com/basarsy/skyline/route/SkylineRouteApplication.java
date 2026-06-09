package com.basarsy.skyline.route;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableCaching
@EnableJpaAuditing
public class SkylineRouteApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkylineRouteApplication.class, args);
    }
}
