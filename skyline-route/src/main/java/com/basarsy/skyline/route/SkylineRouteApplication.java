package com.basarsy.skyline.route;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableCaching
public class SkylineRouteApplication {
    public static void main(String[] args) {
        // UTC is the contract for every LocalDateTime: pin the JVM so now() means the same on any host.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SkylineRouteApplication.class, args);
    }
}
