package com.basarsy.skyline.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.route.dto.RouteRequest;
import com.basarsy.skyline.route.dto.RouteResponse;
import com.basarsy.skyline.route.entity.Airport;
import com.basarsy.skyline.route.mapper.AirportMapperImpl;
import com.basarsy.skyline.route.mapper.RouteMapperImpl;
import com.basarsy.skyline.route.repository.AirportRepository;
import com.basarsy.skyline.route.repository.RouteRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class RouteServiceImplTest {

    private final RouteRepository routes = mock(RouteRepository.class);
    private final AirportRepository airports = mock(AirportRepository.class);
    private final RouteServiceImpl service = new RouteServiceImpl(routes, airports, mapper());

    private final Airport ist = airport("IST", "Europe/Istanbul");
    private final Airport lhr = airport("LHR", "Europe/London");

    @Test
    void createsARouteBetweenTwoKnownAirports() {
        when(airports.findById(ist.getId())).thenReturn(Optional.of(ist));
        when(airports.findById(lhr.getId())).thenReturn(Optional.of(lhr));
        when(routes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RouteResponse created = service.create(new RouteRequest(ist.getId(), lhr.getId(), 2500, 240));

        assertThat(created.origin().iataCode()).isEqualTo("IST");
        assertThat(created.destination().iataCode()).isEqualTo("LHR");
        assertThat(created.distanceKm()).isEqualTo(2500);
        assertThat(created.estimatedDurationMinutes()).isEqualTo(240);
    }

    @Test
    void sameOriginAndDestinationIsA400BeforeAnyLookup() {
        assertThatThrownBy(() -> service.create(new RouteRequest(ist.getId(), ist.getId(), 1, 1)))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Origin and destination must differ")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(airports, routes);
    }

    @Test
    void unknownOriginIsA404() {
        when(airports.findById(ist.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new RouteRequest(ist.getId(), lhr.getId(), 2500, 240)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Origin airport not found");
        verify(routes, never()).save(any());
    }

    @Test
    void unknownDestinationIsA404() {
        when(airports.findById(ist.getId())).thenReturn(Optional.of(ist));
        when(airports.findById(lhr.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new RouteRequest(ist.getId(), lhr.getId(), 2500, 240)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Destination airport not found");
        verify(routes, never()).save(any());
    }

    @Test
    void findByIdOfAnUnknownRouteIsA404() {
        UUID unknown = UUID.randomUUID();
        when(routes.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(unknown))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknown.toString());
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

    private static RouteMapperImpl mapper() {
        RouteMapperImpl mapper = new RouteMapperImpl();
        ReflectionTestUtils.setField(mapper, "airportMapper", new AirportMapperImpl());
        return mapper;
    }
}
