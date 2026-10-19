package com.basarsy.skyline.fleet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableCaching
public class SkylineFleetApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkylineFleetApplication.class, args);
    }
}
