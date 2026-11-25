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
import com.basarsy.skyline.fleet.client.ScheduleClient;
import com.basarsy.skyline.fleet.dto.AircraftTypeRequest;
import com.basarsy.skyline.fleet.dto.UpdateAircraftStatusRequest;
import com.basarsy.skyline.fleet.entity.Aircraft;
import com.basarsy.skyline.fleet.entity.AircraftStatus;
import com.basarsy.skyline.fleet.entity.AircraftType;
import com.basarsy.skyline.fleet.mapper.AircraftMapper;
import com.basarsy.skyline.fleet.mapper.AircraftTypeMapperImpl;
import com.basarsy.skyline.fleet.repository.AircraftRepository;
import com.basarsy.skyline.fleet.repository.AircraftTypeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AircraftStatusAndTypeRulesTest {

    private final AircraftRepository aircraftRepository = mock(AircraftRepository.class);
    private final AircraftTypeRepository typeRepository = mock(AircraftTypeRepository.class);
    private final ScheduleClient scheduleClient = mock(ScheduleClient.class);
    private final AircraftServiceImpl aircraftService =
            new AircraftServiceImpl(aircraftRepository, typeRepository, mock(AircraftMapper.class), scheduleClient);
    private final AircraftTypeServiceImpl typeService = new AircraftTypeServiceImpl(typeRepository, new AircraftTypeMapperImpl());

    @AfterEach
    void clearCaller() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeAndMaintenanceMoveFreelyAndAnAdminRetiresAnAircraftWithNoFlights() {
        loginAs("ROLE_ADMIN");
        Aircraft aircraft = aircraft(AircraftStatus.ACTIVE);
        when(scheduleClient.flightsOnAircraft(aircraft.getId())).thenReturn(List.of());

        aircraftService.updateStatus(aircraft.getId(), new UpdateAircraftStatusRequest(AircraftStatus.MAINTENANCE));
        aircraftService.updateStatus(aircraft.getId(), new UpdateAircraftStatusRequest(AircraftStatus.ACTIVE));
        aircraftService.updateStatus(aircraft.getId(), new UpdateAircraftStatusRequest(AircraftStatus.RETIRED));

        assertThat(aircraft.getStatus()).isEqualTo(AircraftStatus.RETIRED);
    }

    @Test
    void anAircraftThatStillFliesIsNotRetired() {
        loginAs("ROLE_ADMIN");
        Aircraft aircraft = aircraft(AircraftStatus.ACTIVE);
        aircraft.setTailNumber("TC-JFA");
        when(scheduleClient.flightsOnAircraft(aircraft.getId())).thenReturn(List.of("SK101", "SK205"));

        assertThatThrownBy(() -> aircraftService.updateStatus(aircraft.getId(),
                new UpdateAircraftStatusRequest(AircraftStatus.RETIRED)))
                .isInstanceOfSatisfying(SkylineException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).startsWith("Aircraft TC-JFA still flies SK101, SK205");
                });
        assertThat(aircraft.getStatus()).isEqualTo(AircraftStatus.ACTIVE);
    }

    @Test
    void onlyAnAdminRetiresAnAircraft() {
        loginAs("ROLE_STAFF");
        Aircraft aircraft = aircraft(AircraftStatus.ACTIVE);

        assertThatThrownBy(() -> aircraftService.updateStatus(aircraft.getId(),
                new UpdateAircraftStatusRequest(AircraftStatus.RETIRED)))
                .isInstanceOfSatisfying(SkylineException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(scheduleClient, never()).flightsOnAircraft(any());
        assertThat(aircraft.getStatus()).isEqualTo(AircraftStatus.ACTIVE);
    }

    private static void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", null,
                List.of(new SimpleGrantedAuthority(role))));
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
