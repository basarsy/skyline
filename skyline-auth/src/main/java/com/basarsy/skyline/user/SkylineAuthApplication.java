package com.basarsy.skyline.user;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
public class SkylineAuthApplication {
    public static void main(String[] args) {
        // UTC is the contract for every LocalDateTime: pin the JVM so now() means the same on any host.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SkylineAuthApplication.class, args);
    }
}
