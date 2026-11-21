package com.basarsy.skyline.fleet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.fleet.dto.AircraftTypeRequest;
import com.basarsy.skyline.fleet.dto.UpdateAircraftStatusRequest;
import com.basarsy.skyline.fleet.entity.Aircraft;
import com.basarsy.skyline.fleet.entity.AircraftStatus;
import com.basarsy.skyline.fleet.entity.AircraftType;
import com.basarsy.skyline.fleet.mapper.AircraftMapper;
import com.basarsy.skyline.fleet.mapper.AircraftTypeMapperImpl;
import com.basarsy.skyline.fleet.repository.AircraftRepository;
import com.basarsy.skyline.fleet.repository.AircraftTypeRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class AircraftStatusAndTypeRulesTest {

    private final AircraftRepository aircraftRepository = mock(AircraftRepository.class);
    private final AircraftTypeRepository typeRepository = mock(AircraftTypeRepository.class);
    private final AircraftServiceImpl aircraftService =
            new AircraftServiceImpl(aircraftRepository, typeRepository, mock(AircraftMapper.class));
    private final AircraftTypeServiceImpl typeService = new AircraftTypeServiceImpl(typeRepository, new AircraftTypeMapperImpl());

    @Test
    void activeAndMaintenanceMoveFreelyAndAnyStatusCanRetire() {
        Aircraft aircraft = aircraft(AircraftStatus.ACTIVE);

        aircraftService.updateStatus(aircraft.getId(), new UpdateAircraftStatusRequest(AircraftStatus.MAINTENANCE));
        aircraftService.updateStatus(aircraft.getId(), new UpdateAircraftStatusRequest(AircraftStatus.ACTIVE));
        aircraftService.updateStatus(aircraft.getId(), new UpdateAircraftStatusRequest(AircraftStatus.RETIRED));

        assertThat(aircraft.getStatus()).isEqualTo(AircraftStatus.RETIRED);
    }

    @Test
    void retiredIsTerminal() {
        Aircraft aircraft = aircraft(AircraftStatus.RETIRED);

        for (AircraftStatus next : new AircraftStatus[] {AircraftStatus.ACTIVE, AircraftStatus.MAINTENANCE}) {
            assertThatThrownBy(() -> aircraftService.updateStatus(aircraft.getId(), new UpdateAircraftStatusRequest(next)))
                    .isInstanceOfSatisfying(SkylineException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }
        assertThat(aircraft.getStatus()).isEqualTo(AircraftStatus.RETIRED);
    }

    @Test
    void aircraftTypeNamesAreTrimmedBeforeTheDuplicateCheckAndSave() {
        when(typeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        typeService.create(new AircraftTypeRequest("  Airbus ", " A321neo  ", 220, null));

        verify(typeRepository).existsByManufacturerIgnoreCaseAndModelIgnoreCase("Airbus", "A321neo");
        var saved = ArgumentCaptor.forClass(AircraftType.class);
        verify(typeRepository).save(saved.capture());
        assertThat(saved.getValue().getManufacturer()).isEqualTo("Airbus");
        assertThat(saved.getValue().getModel()).isEqualTo("A321neo");
    }

    @Test
    void aDuplicateAircraftTypeIsA409() {
        when(typeRepository.existsByManufacturerIgnoreCaseAndModelIgnoreCase(eq("Airbus"), anyString())).thenReturn(true);

        assertThatThrownBy(() -> typeService.create(new AircraftTypeRequest(" Airbus", "A320neo", 180, null)))
                .isInstanceOfSatisfying(SkylineException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(typeRepository, never()).save(any());
    }

    private Aircraft aircraft(AircraftStatus status) {
        Aircraft aircraft = new Aircraft();
        aircraft.setId(UUID.randomUUID());
        aircraft.setStatus(status);
        when(aircraftRepository.findByIdWithAircraftType(aircraft.getId())).thenReturn(Optional.of(aircraft));
        return aircraft;
    }
}
