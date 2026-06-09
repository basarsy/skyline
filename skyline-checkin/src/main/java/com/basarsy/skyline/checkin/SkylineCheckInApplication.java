package com.basarsy.skyline.checkin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableJpaAuditing
@EnableFeignClients
public class SkylineCheckInApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkylineCheckInApplication.class, args);
    }
}
