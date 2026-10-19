package com.basarsy.skyline.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
public class SkylineAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkylineAuthApplication.class, args);
    }
}
