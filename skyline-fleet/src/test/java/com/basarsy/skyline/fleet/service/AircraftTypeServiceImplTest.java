package com.basarsy.skyline.fleet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.fleet.dto.AircraftTypeRequest;
import com.basarsy.skyline.fleet.dto.AircraftTypeResponse;
import com.basarsy.skyline.fleet.mapper.AircraftTypeMapperImpl;
import com.basarsy.skyline.fleet.repository.AircraftTypeRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AircraftTypeServiceImplTest {

    private final AircraftTypeRepository types = mock(AircraftTypeRepository.class);
    private final AircraftTypeServiceImpl service = new AircraftTypeServiceImpl(types, new AircraftTypeMapperImpl());

    private final AircraftTypeRequest request = new AircraftTypeRequest("Airbus", "A321neo", 220, "Y220");

    @Test
    void createsANewType() {
        when(types.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AircraftTypeResponse created = service.create(request);

        assertThat(created).isEqualTo(new AircraftTypeResponse(null, "Airbus", "A321neo", 220, "Y220"));
    }

    @Test
    void duplicateManufacturerAndModelIsA409AndNothingIsSaved() {
        when(types.existsByManufacturerIgnoreCaseAndModelIgnoreCase("Airbus", "A321neo")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Aircraft type already exists")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(types, never()).save(any());
    }

    @Test
    void findByIdOfAnUnknownTypeIsA404() {
        UUID unknown = UUID.randomUUID();
        when(types.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(unknown)).isInstanceOf(ResourceNotFoundException.class);
    }
}
