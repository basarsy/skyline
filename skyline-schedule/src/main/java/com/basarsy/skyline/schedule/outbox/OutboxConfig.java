package com.basarsy.skyline.schedule.outbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduling is enabled here, in the schedule service only, for {@link OutboxScheduler}. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxConfig {}
