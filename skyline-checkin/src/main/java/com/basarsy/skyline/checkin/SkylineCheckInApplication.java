package com.basarsy.skyline.checkin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableFeignClients
public class SkylineCheckInApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkylineCheckInApplication.class, args);
    }
}
