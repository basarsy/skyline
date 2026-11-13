package com.basarsy.skyline.route.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.basarsy.skyline.route.dto.RouteResponse;
import com.basarsy.skyline.route.entity.Airport;
import com.basarsy.skyline.route.entity.Route;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RouteMapperTest {

    private final RouteMapper mapper = routeMapper();

    @Test
    void embeddedAirportsCarryTheirTimeZone() {
        Route route = new Route();
        route.setId(UUID.randomUUID());
        route.setOrigin(airport("IST", "Europe/Istanbul"));
        route.setDestination(airport("LHR", "Europe/London"));
        route.setDistanceKm(2500);
        route.setEstimatedDurationMinutes(240);

        RouteResponse response = mapper.toResponse(route);

        assertThat(response.origin().timezone()).isEqualTo("Europe/Istanbul");
        assertThat(response.destination().timezone()).isEqualTo("Europe/London");
        assertThat(response.origin().iataCode()).isEqualTo("IST");
    }

    private static Airport airport(String iata, String timezone) {
        Airport airport = new Airport();
        airport.setId(UUID.randomUUID());
        airport.setIataCode(iata);
        airport.setName(iata + " airport");
        airport.setCity("City");
        airport.setCountry("Country");
        airport.setTimezone(timezone);
        return airport;
    }

    private static RouteMapper routeMapper() {
        RouteMapperImpl routeMapper = new RouteMapperImpl();
        ReflectionTestUtils.setField(routeMapper, "airportMapper", new AirportMapperImpl());
        return routeMapper;
    }
}
