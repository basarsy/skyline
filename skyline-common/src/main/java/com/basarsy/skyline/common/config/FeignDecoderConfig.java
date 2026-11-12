package com.basarsy.skyline.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.Decoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignDecoderConfig {

    /**
     * Replaces OpenFeign's default decoder (which is {@code @ConditionalOnMissingBean}) for every
     * Feign client so that {@code ApiResponse} envelopes are unwrapped transparently.
     */
    @Bean
    public Decoder feignDecoder(ObjectMapper objectMapper) {
        return new ApiResponseUnwrappingDecoder(objectMapper);
    }
}
