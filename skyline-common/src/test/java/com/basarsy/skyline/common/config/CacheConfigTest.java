package com.basarsy.skyline.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

class CacheConfigTest {

    record CachedDto(UUID id, LocalDateTime departure, LocalDate expiry) {}

    private final RedisSerializer<Object> serializer = CacheConfig.redisValueSerializer();

    @Test
    void roundTripsRecordsWithJavaTimeFields() {
        CachedDto dto = new CachedDto(UUID.randomUUID(), LocalDateTime.of(2026, 11, 14, 9, 30), LocalDate.of(2027, 1, 1));

        Object restored = serializer.deserialize(serializer.serialize(dto));

        assertThat(restored).isEqualTo(dto);
    }

    @Test
    void roundTripsImmutableListsReturnedByStreamToList() {
        List<CachedDto> list = Stream.of(
                        new CachedDto(UUID.randomUUID(), LocalDateTime.of(2026, 1, 1, 8, 0), LocalDate.of(2027, 1, 1)),
                        new CachedDto(UUID.randomUUID(), LocalDateTime.of(2026, 1, 2, 8, 0), LocalDate.of(2027, 1, 2)))
                .toList();

        Object restored = serializer.deserialize(serializer.serialize(list));

        assertThat(restored).isEqualTo(list);
    }

    @Test
    void roundTripsListOf() {
        List<CachedDto> list = List.of(new CachedDto(UUID.randomUUID(), LocalDateTime.of(2026, 1, 1, 8, 0), LocalDate.of(2027, 1, 1)));

        assertThat(serializer.deserialize(serializer.serialize(list))).isEqualTo(list);
    }
}
