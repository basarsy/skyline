package com.basarsy.skyline.route;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.basarsy.skyline.route.dto.AirportResponse;
import com.basarsy.skyline.route.entity.Airport;
import com.basarsy.skyline.route.mapper.AirportMapper;
import com.basarsy.skyline.route.repository.AirportRepository;
import com.basarsy.skyline.route.service.AirportService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class AirportServiceCacheTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.0-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private AirportService airportService;

    @MockitoBean
    private AirportRepository airportRepository;

    @Autowired
    private AirportMapper airportMapper;

    @Test
    void testFindByIdCaching() {
        UUID id = UUID.randomUUID();
        Airport airport = new Airport();
        airport.setId(id);
        airport.setIataCode("LHR");
        airport.setName("London Heathrow");

        when(airportRepository.findById(id)).thenReturn(Optional.of(airport));

        // First call - should hit repository
        AirportResponse response1 = airportService.findById(id);
        assertNotNull(response1);
        assertEquals("LHR", response1.iataCode());

        // Second call - should return from cache
        AirportResponse response2 = airportService.findById(id);
        assertNotNull(response2);
        assertEquals("LHR", response2.iataCode());

        // Verify repository was called only once
        verify(airportRepository, times(1)).findById(id);
    }
}
