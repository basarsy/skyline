package com.basarsy.skyline.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
public class SkylineGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkylineGatewayApplication.class, args);
    }
}
