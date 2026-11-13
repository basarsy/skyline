package com.basarsy.skyline.gateway;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SkylineGatewayApplication {
    public static void main(String[] args) {
        // UTC is the contract for every LocalDateTime: pin the JVM so now() means the same on any host.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SkylineGatewayApplication.class, args);
    }
}
