package com.basarsy.skyline.notification;

import com.basarsy.skyline.notification.config.NotificationProperties;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.basarsy.skyline")
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class SkylineNotificationApplication {
    public static void main(String[] args) {
        // UTC is the contract for every LocalDateTime: pin the JVM so now() means the same on any host.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SkylineNotificationApplication.class, args);
    }
}
