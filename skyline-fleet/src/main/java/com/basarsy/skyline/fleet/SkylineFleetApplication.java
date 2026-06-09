package com.basarsy.skyline.fleet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableCaching
@EnableJpaAuditing
public class SkylineFleetApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkylineFleetApplication.class, args);
    }
}
