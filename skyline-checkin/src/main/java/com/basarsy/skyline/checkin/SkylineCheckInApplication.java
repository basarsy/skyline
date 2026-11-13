package com.basarsy.skyline.checkin;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableFeignClients
public class SkylineCheckInApplication {
    public static void main(String[] args) {
        // UTC is the contract for every LocalDateTime: pin the JVM so now() means the same on any host.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SkylineCheckInApplication.class, args);
    }
}
