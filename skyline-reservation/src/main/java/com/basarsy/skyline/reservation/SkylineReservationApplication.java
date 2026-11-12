package com.basarsy.skyline.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableFeignClients
public class SkylineReservationApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkylineReservationApplication.class, args);
    }
}
