package com.basarsy.skyline.route;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableCaching
public class SkylineRouteApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkylineRouteApplication.class, args);
    }
}
