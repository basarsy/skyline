package com.basarsy.skyline.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.crew.client.ScheduleClient;
import com.basarsy.skyline.crew.client.dto.FlightResponse;
import com.basarsy.skyline.crew.client.dto.FlightStatus;
import com.basarsy.skyline.crew.dto.CrewMemberRequest;
import com.basarsy.skyline.crew.dto.CrewMemberResponse;
import com.basarsy.skyline.crew.dto.FlightCrewAssignmentRequest;
import com.basarsy.skyline.crew.entity.CrewMember;
import com.basarsy.skyline.crew.entity.CrewRole;
import com.basarsy.skyline.crew.entity.FlightCrew;
import com.basarsy.skyline.crew.mapper.CrewMapperImpl;
import com.basarsy.skyline.crew.repository.CrewMemberRepository;
import com.basarsy.skyline.crew.repository.FlightCrewRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

class CrewServiceImplTest {

    private final CrewMemberRepository members = mock(CrewMemberRepository.class);
    private final FlightCrewRepository assignments = mock(FlightCrewRepository.class);
    private final ScheduleClient scheduleClient = mock(ScheduleClient.class);
    private final CrewServiceImpl service = new CrewServiceImpl(members, assignments, scheduleClient,
            new CrewMapperImpl());

    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2030, 6, 1, 9, 0);

    @Test
    void aCrewMemberCanOnlyBeAssignedInTheirOwnRole() {
        CrewMember attendant = crewMember(CrewRole.FLIGHT_ATTENDANT);
        UUID flightId = stubFlight(FlightStatus.SCHEDULED, DEPARTURE);

        assertThatThrownBy(() -> service.assignCrewToFlight(flightId,
                new FlightCrewAssignmentRequest(attendant.getId(), CrewRole.CAPTAIN)))
                .isInstanceOfSatisfying(SkylineException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(assignments, never()).save(any());
    }

    @Test
    void cancelledAndArrivedFlightsDoNotBlockAnOverlappingAssignment() {
        CrewMember captain = crewMember(CrewRole.CAPTAIN);
        UUID flightId = stubFlight(FlightStatus.SCHEDULED, DEPARTURE);
        UUID cancelled = stubFlight(FlightStatus.CANCELLED, DEPARTURE.plusHours(1));
        UUID arrived = stubFlight(FlightStatus.ARRIVED, DEPARTURE.minusHours(1));
        when(assignments.findByCrewMemberId(captain.getId()))
                .thenReturn(List.of(assignment(cancelled, captain), assignment(arrived, captain)));
        when(assignments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.assignCrewToFlight(flightId, new FlightCrewAssignmentRequest(captain.getId(), CrewRole.CAPTAIN));

        verify(assignments).save(any());
    }

    @Test
    void anOperatingOverlappingFlightStillBlocksTheAssignment() {
        CrewMember captain = crewMember(CrewRole.CAPTAIN);
        UUID flightId = stubFlight(FlightStatus.SCHEDULED, DEPARTURE);
        UUID delayed = stubFlight(FlightStatus.DELAYED, DEPARTURE.plusHours(1));
        when(assignments.findByCrewMemberId(captain.getId())).thenReturn(List.of(assignment(delayed, captain)));

        assertThatThrownBy(() -> service.assignCrewToFlight(flightId,
                new FlightCrewAssignmentRequest(captain.getId(), CrewRole.CAPTAIN)))
                .isInstanceOfSatisfying(SkylineException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private CrewMember crewMember(CrewRole role) {
        CrewMember member = new CrewMember();
        member.setId(UUID.randomUUID());
        member.setRole(role);
        member.setLicenseExpiryDate(LocalDate.of(2035, 1, 1));
        when(members.findById(member.getId())).thenReturn(Optional.of(member));
        return member;
    }

    private UUID stubFlight(FlightStatus status, LocalDateTime departure) {
        UUID id = UUID.randomUUID();
        when(scheduleClient.getFlight(id)).thenReturn(FlightResponse.builder().id(id).status(status)
                .departureTime(departure).arrivalTime(departure.plusHours(4)).build());
        return id;
    }

    private static FlightCrew assignment(UUID flightId, CrewMember member) {
        FlightCrew assignment = new FlightCrew();
        assignment.setFlightId(flightId);
        assignment.setCrewMember(member);
        assignment.setRole(member.getRole());
        return assignment;
    }

    @Test
    void duplicateEmployeeNumberIsA409WithAClearMessage() {
        when(members.existsByEmployeeNumberIgnoreCase("E100")).thenReturn(true);

        assertThatThrownBy(() -> service.addCrewMember(request("E100")))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Employee number already exists")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(members, never()).save(any());
    }

    @Test
    void aUserCanOnlyBeOneCrewMember() {
        CrewMemberRequest request = request("E101");
        when(members.existsByUserId(request.userId())).thenReturn(true);

        assertThatThrownBy(() -> service.addCrewMember(request))
                .isInstanceOf(SkylineException.class)
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void addsANewCrewMember() {
        when(members.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CrewMemberResponse response = service.addCrewMember(request("E102"));

        assertThat(response.employeeNumber()).isEqualTo("E102");
        assertThat(response.role()).isEqualTo(CrewRole.CAPTAIN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listingSortsByLastNameThenFirstName() {
        CrewMember member = new CrewMember();
        member.setEmployeeNumber("E1");
        member.setLastName("Lovelace");
        when(members.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(member));

        assertThat(service.listCrewMembers(CrewRole.CAPTAIN, "love")).extracting(CrewMemberResponse::lastName)
                .containsExactly("Lovelace");
        verify(members).findAll(any(Specification.class), eq(Sort.by(Sort.Order.asc("lastName").ignoreCase(),
                Sort.Order.asc("firstName").ignoreCase(), Sort.Order.asc("employeeNumber"))));
    }

    @Test
    void unknownCrewMemberIsA404() {
        UUID id = UUID.randomUUID();
        when(members.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCrewMember(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static CrewMemberRequest request(String employeeNumber) {
        return new CrewMemberRequest(UUID.randomUUID(), employeeNumber, "Ada", "Lovelace", CrewRole.CAPTAIN,
                "LIC-1", LocalDate.of(2035, 1, 1));
    }
}
