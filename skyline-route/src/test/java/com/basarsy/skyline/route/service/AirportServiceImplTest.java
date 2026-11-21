package com.basarsy.skyline.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.route.dto.AirportRequest;
import com.basarsy.skyline.route.dto.AirportResponse;
import com.basarsy.skyline.route.mapper.AirportMapperImpl;
import com.basarsy.skyline.route.repository.AirportRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AirportServiceImplTest {

    private final AirportRepository airports = mock(AirportRepository.class);
    private final AirportServiceImpl service = new AirportServiceImpl(airports, new AirportMapperImpl());

    private final AirportRequest request =
            new AirportRequest("saw", "ltfj", "Sabiha Gokcen", "Istanbul", "Turkey", "Europe/Istanbul");

    @Test
    void createUppercasesTheCodesAndKeepsTheRest() {
        when(airports.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AirportResponse created = service.create(request);

        assertThat(created).isEqualTo(new AirportResponse(null, "SAW", "LTFJ", "Sabiha Gokcen", "Istanbul", "Turkey",
                "Europe/Istanbul"));
    }

    @Test
    void duplicateIataCodeIsA409AndNothingIsSaved() {
        when(airports.existsByIataCode("SAW")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Airport with IATA code already exists")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(airports, never()).save(any());
    }

    @Test
    void findByIdOfAnUnknownAirportIsA404() {
        UUID unknown = UUID.randomUUID();
        when(airports.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(unknown))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }
}
