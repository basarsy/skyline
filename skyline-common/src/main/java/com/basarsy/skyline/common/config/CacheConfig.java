package com.basarsy.skyline.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisValueSerializer()));
    }

    /**
     * JSON serializer for cached values. Two fixes over the stock {@link GenericJackson2JsonRedisSerializer}:
     *
     * <ul>
     *   <li>Its internal ObjectMapper has no java.time support, so any DTO with a LocalDate/LocalDateTime
     *       failed to serialize.
     *   <li>It records the concrete class of every value, and JDK immutable collections (returned by
     *       {@code List.of} / {@code Stream.toList()}) cannot be re-instantiated from that type id. Cached
     *       methods commonly return such lists, so top-level collections are copied into
     *       ArrayList/LinkedHashSet before writing. (Cached DTOs should not hold immutable collections in
     *       their fields for the same reason.)
     * </ul>
     */
    static RedisSerializer<Object> redisValueSerializer() {
        GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer();
        json.configure(mapper -> mapper
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

        return new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) {
                Object normalized = value;
                if (value instanceof List<?> list) {
                    normalized = new ArrayList<>(list);
                } else if (value instanceof Set<?> set) {
                    normalized = new LinkedHashSet<>(set);
                }
                return json.serialize(normalized);
            }

            @Override
            public Object deserialize(byte[] bytes) {
                return json.deserialize(bytes);
            }
        };
    }
}
