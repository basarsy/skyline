package com.basarsy.skyline.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.route.dto.AirportRequest;
import com.basarsy.skyline.route.dto.RouteRequest;
import com.basarsy.skyline.route.entity.Airport;
import com.basarsy.skyline.route.entity.Route;
import com.basarsy.skyline.route.mapper.AirportMapperImpl;
import com.basarsy.skyline.route.mapper.RouteMapper;
import com.basarsy.skyline.route.repository.AirportRepository;
import com.basarsy.skyline.route.repository.RouteRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class RouteAndAirportServiceTest {

    private final RouteRepository routes = mock(RouteRepository.class);
    private final AirportRepository airports = mock(AirportRepository.class);
    private final RouteServiceImpl routeService = new RouteServiceImpl(routes, airports, mock(RouteMapper.class));
    private final AirportServiceImpl airportService = new AirportServiceImpl(airports, new AirportMapperImpl());

    @Test
    void routeSearchIgnoresCaseEvenOnATurkishLocaleHost() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); // where "ist".toUpperCase() is "İST"
            when(routes.findByOrigin_IataCodeAndDestination_IataCode("IST", "LHR")).thenReturn(List.of(new Route()));

            assertThat(routeService.findByOriginAndDestination(" ist", "lhr ")).hasSize(1);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void aDuplicateRouteIsA409WithAClearMessage() {
        Airport ist = airport("IST");
        Airport lhr = airport("LHR");
        when(routes.existsByOrigin_IdAndDestination_Id(ist.getId(), lhr.getId())).thenReturn(true);

        assertThatThrownBy(() -> routeService.create(new RouteRequest(ist.getId(), lhr.getId(), 2500, 240)))
                .isInstanceOfSatisfying(SkylineException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("Route from IST to LHR already exists");
                });
        verify(routes, never()).save(any());
    }

    @Test
    void airportCodesAreTrimmedAndUpperCased() {
        when(airports.save(any())).thenAnswer(inv -> inv.getArgument(0));

        airportService.create(request("saw", "ltfj"));

        var saved = ArgumentCaptor.forClass(Airport.class);
        verify(airports).save(saved.capture());
        assertThat(saved.getValue().getIataCode()).isEqualTo("SAW");
        assertThat(saved.getValue().getIcaoCode()).isEqualTo("LTFJ");
    }

    @Test
    void anIataCodeThatIsNotThreeLettersIsA400() {
        for (String bad : List.of("S1W", "S W", "SÄW")) {
            assertThatThrownBy(() -> airportService.create(request(bad, "LTFJ")))
                    .isInstanceOfSatisfying(SkylineException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(airports, never()).save(any());
    }

    @Test
    void aDuplicateIataCodeIsA409() {
        when(airports.existsByIataCode("SAW")).thenReturn(true);

        assertThatThrownBy(() -> airportService.create(request("saw", "LTFJ")))
                .isInstanceOfSatisfying(SkylineException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private Airport airport(String iata) {
        Airport airport = new Airport();
        airport.setId(UUID.randomUUID());
        airport.setIataCode(iata);
        when(airports.findById(airport.getId())).thenReturn(Optional.of(airport));
        return airport;
    }

    private static AirportRequest request(String iata, String icao) {
        return new AirportRequest(iata, icao, "Sabiha Gokcen", "Istanbul", "Turkey", "Europe/Istanbul");
    }
}
