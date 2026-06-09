package com.basarsy.skyline.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableJpaAuditing
public class SkylineAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkylineAuthApplication.class, args);
    }
}
