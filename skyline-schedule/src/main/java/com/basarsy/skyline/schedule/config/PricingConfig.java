package com.basarsy.skyline.schedule.config;

import com.basarsy.skyline.common.pricing.PricingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PricingProperties.class)
public class PricingConfig {
}
