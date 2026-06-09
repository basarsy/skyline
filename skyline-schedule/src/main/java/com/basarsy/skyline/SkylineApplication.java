package com.basarsy.skyline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableFeignClients
public class SkylineApplication {

	public static void main(String[] args) {
		SpringApplication.run(SkylineApplication.class, args);
	}

}
