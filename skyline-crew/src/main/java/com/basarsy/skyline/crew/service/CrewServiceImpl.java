package com.basarsy.skyline.crew.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.crew.client.ScheduleClient;
import com.basarsy.skyline.crew.client.dto.FlightResponse;
import com.basarsy.skyline.crew.dto.CrewMemberRequest;
import com.basarsy.skyline.crew.dto.CrewMemberResponse;
import com.basarsy.skyline.crew.dto.FlightCrewAssignmentRequest;
import com.basarsy.skyline.crew.dto.FlightCrewResponse;
import com.basarsy.skyline.crew.entity.CrewMember;
import com.basarsy.skyline.crew.entity.FlightCrew;
import com.basarsy.skyline.crew.mapper.CrewMapper;
import com.basarsy.skyline.crew.repository.CrewMemberRepository;
import com.basarsy.skyline.crew.repository.FlightCrewRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrewServiceImpl implements CrewService {

    private final CrewMemberRepository crewMemberRepository;
    private final FlightCrewRepository flightCrewRepository;
    private final ScheduleClient scheduleClient;
    private final CrewMapper crewMapper;

    @Override
    @Transactional
    public CrewMemberResponse addCrewMember(CrewMemberRequest request) {
        CrewMember crewMember = crewMapper.toEntity(request);
        crewMember.setUserId(request.userId());

        CrewMember saved = crewMemberRepository.save(crewMember);
        return crewMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public FlightCrewResponse assignCrewToFlight(UUID flightId, FlightCrewAssignmentRequest request) {
        FlightResponse flight = scheduleClient.getFlight(flightId);

        CrewMember crewMember = crewMemberRepository.findById(request.crewMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Crew member not found"));

        // Validation: License expiry
        if (crewMember.getLicenseExpiryDate().isBefore(flight.getDepartureTime().toLocalDate())) {
            throw new SkylineException("Crew member license is expired or will expire before flight", HttpStatus.BAD_REQUEST);
        }

        // Validation: Overlapping assignments
        List<FlightCrew> assignments = flightCrewRepository.findByCrewMemberId(crewMember.getId());
        for (FlightCrew assignment : assignments) {
            FlightResponse assignedFlight = scheduleClient.getFlight(assignment.getFlightId());
            if (assignedFlight.getDepartureTime().isBefore(flight.getArrivalTime()) && 
                assignedFlight.getArrivalTime().isAfter(flight.getDepartureTime())) {
                throw new SkylineException("Crew member is already assigned to an overlapping flight", HttpStatus.CONFLICT);
            }
        }

        FlightCrew flightCrew = new FlightCrew();
        flightCrew.setFlightId(flightId);
        flightCrew.setCrewMember(crewMember);
        flightCrew.setRole(request.role());

        FlightCrew saved = flightCrewRepository.save(flightCrew);
        return crewMapper.toFlightCrewResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "flight-manifests", key = "#flightId")
    public List<FlightCrewResponse> getFlightManifest(UUID flightId) {
        // verify flight exists
        scheduleClient.getFlight(flightId);

        return flightCrewRepository.findByFlightId(flightId).stream()
                .map(crewMapper::toFlightCrewResponse)
                .toList();
    }

    @Override
    @Transactional
    public void removeCrewFromFlight(UUID flightId, UUID crewMemberId) {
        flightCrewRepository.deleteByFlightIdAndCrewMemberId(flightId, crewMemberId);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateCrewForFlight(UUID flightId) {
        List<FlightCrew> crew = flightCrewRepository.findByFlightId(flightId);
        
        boolean hasCaptain = crew.stream().anyMatch(c -> c.getRole() == com.basarsy.skyline.crew.entity.CrewRole.CAPTAIN);
        boolean hasFirstOfficer = crew.stream().anyMatch(c -> c.getRole() == com.basarsy.skyline.crew.entity.CrewRole.FIRST_OFFICER);

        if (!hasCaptain || !hasFirstOfficer) {
            throw new SkylineException("Flight must have at least one CAPTAIN and one FIRST_OFFICER to proceed", HttpStatus.BAD_REQUEST);
        }
    }
}
