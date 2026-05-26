package com.basarsy.skyline.crew.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.crew.dto.CrewMemberRequest;
import com.basarsy.skyline.crew.dto.CrewMemberResponse;
import com.basarsy.skyline.crew.dto.FlightCrewAssignmentRequest;
import com.basarsy.skyline.crew.dto.FlightCrewResponse;
import com.basarsy.skyline.crew.service.CrewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CrewController {

    private final CrewService crewService;

    @PostMapping("/crew")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CrewMemberResponse> addCrewMember(@Valid @RequestBody CrewMemberRequest request) {
        return ApiResponse.success("Crew member added successfully", crewService.addCrewMember(request));
    }

    @PostMapping("/flights/{id}/crew")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<FlightCrewResponse> assignCrewToFlight(
            @PathVariable UUID id,
            @Valid @RequestBody FlightCrewAssignmentRequest request) {
        return ApiResponse.success("Crew assigned to flight successfully", crewService.assignCrewToFlight(id, request));
    }

    @GetMapping("/flights/{id}/crew")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<FlightCrewResponse>> getFlightManifest(@PathVariable UUID id) {
        return ApiResponse.success("Flight crew manifest retrieved successfully", crewService.getFlightManifest(id));
    }

    @DeleteMapping("/flights/{flightId}/crew/{crewMemberId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> removeCrewFromFlight(
            @PathVariable UUID flightId,
            @PathVariable UUID crewMemberId) {
        crewService.removeCrewFromFlight(flightId, crewMemberId);
        return ApiResponse.success("Crew member removed from flight successfully", null);
    }
}
