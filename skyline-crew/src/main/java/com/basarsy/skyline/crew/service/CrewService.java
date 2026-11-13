package com.basarsy.skyline.crew.service;

import com.basarsy.skyline.crew.dto.CrewMemberRequest;
import com.basarsy.skyline.crew.dto.CrewMemberResponse;
import com.basarsy.skyline.crew.dto.FlightCrewAssignmentRequest;
import com.basarsy.skyline.crew.dto.FlightCrewResponse;
import com.basarsy.skyline.crew.entity.CrewRole;
import java.util.List;
import java.util.UUID;

public interface CrewService {
    CrewMemberResponse addCrewMember(CrewMemberRequest request);

    /** Crew directory, sorted by last name; {@code role} and {@code q} are optional filters. */
    List<CrewMemberResponse> listCrewMembers(CrewRole role, String q);

    CrewMemberResponse getCrewMember(UUID id);
    FlightCrewResponse assignCrewToFlight(UUID flightId, FlightCrewAssignmentRequest request);
    List<FlightCrewResponse> getFlightManifest(UUID flightId);
    void removeCrewFromFlight(UUID flightId, UUID crewMemberId);
    void validateCrewForFlight(UUID flightId);
}
