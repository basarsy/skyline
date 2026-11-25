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
import com.basarsy.skyline.fleet.client.ScheduleClient;
import com.basarsy.skyline.fleet.dto.AircraftRequest;
import com.basarsy.skyline.fleet.dto.AircraftResponse;
import com.basarsy.skyline.fleet.entity.Aircraft;
import com.basarsy.skyline.fleet.entity.AircraftStatus;
import com.basarsy.skyline.fleet.entity.AircraftType;
import com.basarsy.skyline.fleet.mapper.AircraftMapperImpl;
import com.basarsy.skyline.fleet.mapper.AircraftTypeMapperImpl;
import com.basarsy.skyline.fleet.repository.AircraftRepository;
import com.basarsy.skyline.fleet.repository.AircraftTypeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class AircraftServiceImplTest {

    private final AircraftRepository aircraft = mock(AircraftRepository.class);
    private final AircraftTypeRepository types = mock(AircraftTypeRepository.class);
    private final AircraftServiceImpl service = new AircraftServiceImpl(aircraft, types, mapper(),
            mock(ScheduleClient.class));

    private final AircraftType a320 = type();

    @Test
    void createNormalizesTheTailNumberAndStartsActive() {
        when(types.findById(a320.getId())).thenReturn(Optional.of(a320));
        when(aircraft.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AircraftResponse created = service.create(new AircraftRequest("  tc-sky ", a320.getId(), 2020));

        ArgumentCaptor<Aircraft> saved = ArgumentCaptor.forClass(Aircraft.class);
        verify(aircraft).save(saved.capture());
        assertThat(saved.getValue().getTailNumber()).isEqualTo("TC-SKY");
        assertThat(saved.getValue().getAircraftType()).isSameAs(a320);
        assertThat(created.status()).isEqualTo(AircraftStatus.ACTIVE);
        assertThat(created.manufacturedYear()).isEqualTo(2020);
        assertThat(created.aircraftType().model()).isEqualTo("A320neo");
        assertThat(created.aircraftType().totalSeats()).isEqualTo(180);
    }

    @Test
    void duplicateTailNumberIsA409AndNothingIsSaved() {
        when(aircraft.existsByTailNumberIgnoreCase("TC-SKY")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new AircraftRequest("tc-sky", a320.getId(), 2020)))
                .isInstanceOf(SkylineException.class)
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(aircraft, never()).save(any());
    }

    @Test
    void unknownAircraftTypeIsA404AndNothingIsSaved() {
        UUID unknown = UUID.randomUUID();
        when(types.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new AircraftRequest("TC-SKY", unknown, 2020)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(aircraft, never()).save(any());
    }

    @Test
    void findByIdOfAnUnknownAircraftIsA404() {
        UUID unknown = UUID.randomUUID();
        when(aircraft.findByIdWithAircraftType(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(unknown))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void findAllMapsEachAircraftWithItsType() {
        Aircraft existing = new Aircraft();
        existing.setId(UUID.randomUUID());
        existing.setTailNumber("TC-ONE");
        existing.setAircraftType(a320);
        existing.setStatus(AircraftStatus.MAINTENANCE);
        when(aircraft.findAllWithAircraftType()).thenReturn(List.of(existing));

        assertThat(service.findAll()).singleElement().satisfies(response -> {
            assertThat(response.tailNumber()).isEqualTo("TC-ONE");
            assertThat(response.aircraftType().id()).isEqualTo(a320.getId());
        });
    }

    private static AircraftType type() {
        AircraftType type = new AircraftType();
        type.setId(UUID.randomUUID());
        type.setManufacturer("Airbus");
        type.setModel("A320neo");
        type.setTotalSeats(180);
        return type;
    }

    private static AircraftMapperImpl mapper() {
        AircraftMapperImpl mapper = new AircraftMapperImpl();
        ReflectionTestUtils.setField(mapper, "aircraftTypeMapper", new AircraftTypeMapperImpl());
        return mapper;
    }
}
