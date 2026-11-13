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
import com.basarsy.skyline.crew.dto.CrewMemberRequest;
import com.basarsy.skyline.crew.dto.CrewMemberResponse;
import com.basarsy.skyline.crew.entity.CrewMember;
import com.basarsy.skyline.crew.entity.CrewRole;
import com.basarsy.skyline.crew.mapper.CrewMapperImpl;
import com.basarsy.skyline.crew.repository.CrewMemberRepository;
import com.basarsy.skyline.crew.repository.FlightCrewRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

class CrewServiceImplTest {

    private final CrewMemberRepository members = mock(CrewMemberRepository.class);
    private final CrewServiceImpl service = new CrewServiceImpl(members, mock(FlightCrewRepository.class),
            mock(ScheduleClient.class), new CrewMapperImpl());

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
