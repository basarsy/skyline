package com.basarsy.skyline.crew.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.crew.dto.CrewMemberRequest;
import com.basarsy.skyline.crew.dto.CrewMemberResponse;
import com.basarsy.skyline.crew.dto.FlightCrewAssignmentRequest;
import com.basarsy.skyline.crew.dto.FlightCrewResponse;
import com.basarsy.skyline.crew.entity.CrewMember;
import com.basarsy.skyline.crew.entity.FlightCrew;
import com.basarsy.skyline.crew.mapper.CrewMapper;
import com.basarsy.skyline.crew.repository.CrewMemberRepository;
import com.basarsy.skyline.crew.repository.FlightCrewRepository;
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.repository.FlightRepository;
import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.repository.UserRepository;
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
    private final FlightRepository flightRepository;
    private final UserRepository userRepository;
    private final CrewMapper crewMapper;

    @Override
    @Transactional
    public CrewMemberResponse addCrewMember(CrewMemberRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        CrewMember crewMember = crewMapper.toEntity(request);
        crewMember.setUser(user);

        CrewMember saved = crewMemberRepository.save(crewMember);
        return crewMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public FlightCrewResponse assignCrewToFlight(UUID flightId, FlightCrewAssignmentRequest request) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found"));

        CrewMember crewMember = crewMemberRepository.findById(request.crewMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Crew member not found"));

        // Validation: License expiry
        if (crewMember.getLicenseExpiryDate().isBefore(flight.getDepartureTime().toLocalDate())) {
            throw new SkylineException("Crew member license is expired or will expire before flight", HttpStatus.BAD_REQUEST);
        }

        // Validation: Overlapping assignments
        List<FlightCrew> overlaps = flightCrewRepository.findOverlappingAssignments(
                crewMember.getId(), flight.getDepartureTime(), flight.getArrivalTime());
        
        if (!overlaps.isEmpty()) {
            throw new SkylineException("Crew member is already assigned to an overlapping flight", HttpStatus.CONFLICT);
        }

        FlightCrew flightCrew = new FlightCrew();
        flightCrew.setFlight(flight);
        flightCrew.setCrewMember(crewMember);
        flightCrew.setRole(request.role());

        FlightCrew saved = flightCrewRepository.save(flightCrew);
        return crewMapper.toFlightCrewResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "flight-manifests", key = "#flightId")
    public List<FlightCrewResponse> getFlightManifest(UUID flightId) {
        if (!flightRepository.existsById(flightId)) {
            throw new ResourceNotFoundException("Flight not found");
        }
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

