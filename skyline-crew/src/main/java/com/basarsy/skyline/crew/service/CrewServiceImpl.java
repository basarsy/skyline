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
import com.basarsy.skyline.crew.entity.CrewRole;
import com.basarsy.skyline.crew.entity.FlightCrew;
import com.basarsy.skyline.crew.mapper.CrewMapper;
import com.basarsy.skyline.crew.repository.CrewLock;
import com.basarsy.skyline.crew.repository.CrewMemberRepository;
import com.basarsy.skyline.crew.repository.CrewMemberSpecifications;
import com.basarsy.skyline.crew.repository.FlightCrewRepository;
import com.basarsy.skyline.crew.client.dto.FlightStatus;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrewServiceImpl implements CrewService {

    /** Flights crew can still be rostered on (removal stops at BOARDING). */
    private static final Set<FlightStatus> ROSTERABLE =
            EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED, FlightStatus.BOARDING);

    private final CrewMemberRepository crewMemberRepository;
    private final FlightCrewRepository flightCrewRepository;
    private final CrewLock crewLock;
    private final ScheduleClient scheduleClient;
    private final CrewMapper crewMapper;

    @Override
    @Transactional
    public CrewMemberResponse addCrewMember(CrewMemberRequest request) {
        // The unique indexes still guard against a concurrent duplicate (409 from the global handler);
        // these checks just name the conflict.
        if (crewMemberRepository.existsByEmployeeNumberIgnoreCase(request.employeeNumber())) {
            throw new SkylineException("Employee number already exists", HttpStatus.CONFLICT);
        }
        if (crewMemberRepository.existsByUserId(request.userId())) {
            throw new SkylineException("This user is already a crew member", HttpStatus.CONFLICT);
        }

        CrewMember crewMember = crewMapper.toEntity(request);
        crewMember.setUserId(request.userId());

        CrewMember saved = crewMemberRepository.save(crewMember);
        return crewMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CrewMemberResponse> listCrewMembers(CrewRole role, String q) {
        Sort byName = Sort.by(Sort.Order.asc("lastName").ignoreCase(), Sort.Order.asc("firstName").ignoreCase(),
                Sort.Order.asc("employeeNumber"));
        return crewMemberRepository.findAll(CrewMemberSpecifications.matching(role, q), byName).stream()
                .map(crewMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CrewMemberResponse getCrewMember(UUID id) {
        return crewMemberRepository.findById(id)
                .map(crewMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Crew member not found"));
    }

    @Override
    @Transactional
    @CacheEvict(value = "flight-manifests", key = "#flightId")
    public FlightCrewResponse assignCrewToFlight(UUID flightId, FlightCrewAssignmentRequest request) {
        FlightResponse flight = scheduleClient.getFlight(flightId);
        if (!ROSTERABLE.contains(flight.getStatus())) {
            throw new SkylineException("Crew can't be assigned to a flight that is " + flight.getStatus(),
                    HttpStatus.CONFLICT);
        }

        CrewMember crewMember = crewMemberRepository.findById(request.crewMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Crew member not found"));

        // A crew member flies in their own role: a flight attendant can't be rostered as the captain.
        if (request.role() != crewMember.getRole()) {
            throw new SkylineException("Crew member is a " + crewMember.getRole() + " and cannot be assigned as "
                    + request.role(), HttpStatus.BAD_REQUEST);
        }

        // Validation: License expiry
        if (crewMember.getLicenseExpiryDate().isBefore(flight.getDepartureTime().toLocalDate())) {
            throw new SkylineException("Crew member license is expired or will expire before flight", HttpStatus.BAD_REQUEST);
        }

        // Overlapping assignments, asked of the schedule service in one call for the member's whole roster
        // (cancelled and arrived flights no longer occupy them; delayed ones count at their expected times).
        // Under the member's lock: a concurrent assignment of the same person waits for this one to commit.
        crewLock.lock(crewMember.getId());
        List<UUID> otherFlights = flightCrewRepository.findByCrewMemberId(crewMember.getId()).stream()
                .map(FlightCrew::getFlightId)
                .filter(other -> !other.equals(flightId))
                .toList();
        if (!otherFlights.isEmpty()) {
            List<String> overlapping = scheduleClient.overlapping(
                    new ScheduleClient.Overlap(otherFlights, expectedDeparture(flight), expectedArrival(flight)));
            if (!overlapping.isEmpty()) {
                throw new SkylineException("Crew member already flies " + String.join(", ", overlapping)
                        + " at that time", HttpStatus.CONFLICT);
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
    @CacheEvict(value = "flight-manifests", key = "#flightId")
    public void removeCrewFromFlight(UUID flightId, UUID crewMemberId) {
        // From boarding on, the manifest is the record of who flew: it stays as it is.
        FlightStatus status = scheduleClient.getFlight(flightId).getStatus();
        if (!ROSTERABLE.contains(status) || status == FlightStatus.BOARDING) {
            throw new SkylineException("Crew can't be removed from a flight that is " + status, HttpStatus.CONFLICT);
        }
        flightCrewRepository.deleteByFlightIdAndCrewMemberId(flightId, crewMemberId);
    }

    /** When the flight now expects to leave: the delay's estimate, else the scheduled time. */
    private static LocalDateTime expectedDeparture(FlightResponse flight) {
        return flight.getEstimatedDepartureTime() != null ? flight.getEstimatedDepartureTime()
                : flight.getDepartureTime();
    }

    private static LocalDateTime expectedArrival(FlightResponse flight) {
        return flight.getEstimatedArrivalTime() != null ? flight.getEstimatedArrivalTime() : flight.getArrivalTime();
    }

    @Override
    @Transactional(readOnly = true)
    public void validateCrewForFlight(UUID flightId) {
        List<FlightCrew> crew = flightCrewRepository.findByFlightId(flightId);
        
        boolean hasCaptain = crew.stream().anyMatch(c -> c.getRole() == CrewRole.CAPTAIN);
        boolean hasFirstOfficer = crew.stream().anyMatch(c -> c.getRole() == CrewRole.FIRST_OFFICER);

        if (!hasCaptain || !hasFirstOfficer) {
            throw new SkylineException("Flight must have at least one CAPTAIN and one FIRST_OFFICER to proceed", HttpStatus.BAD_REQUEST);
        }
    }
}
