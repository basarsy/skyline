package com.basarsy.skyline.schedule.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.schedule.dto.FlightBoardFilter;
import com.basarsy.skyline.schedule.dto.FlightDelayRequest;
import com.basarsy.skyline.schedule.dto.FlightGateRequest;
import com.basarsy.skyline.schedule.dto.FlightRequest;
import com.basarsy.skyline.schedule.dto.OverlapRequest;
import com.basarsy.skyline.schedule.dto.FlightResponse;
import com.basarsy.skyline.schedule.dto.UpdateFlightStatusRequest;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.service.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    /**
     * Bookable flights between two airports on one day. {@code date} is a calendar day at the origin airport:
     * a 01:30 departure from Istanbul (22:30 UTC the evening before) is found under the date Istanbul shows.
     */
    @GetMapping
    @Operation(summary = "Search bookable flights (SCHEDULED, DELAYED)")
    public ApiResponse<Page<FlightResponse>> searchFlights(
            @Parameter(description = "Origin airport IATA code") @RequestParam String origin,
            @Parameter(description = "Destination airport IATA code") @RequestParam String destination,
            @Parameter(description = "Departure day (yyyy-MM-dd) in the origin airport's time zone; "
                    + "a UTC day if the airport has no valid zone")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Pageable pageable) {
        return ApiResponse.success("Flights retrieved successfully", flightService.searchFlights(origin, destination, date, pageable));
    }

    /**
     * Operations flight board: flights of every status. All filters are optional; {@code status} is
     * repeatable ({@code ?status=DELAYED&status=BOARDING}) and {@code date} is a UTC day. The literal path
     * takes precedence over {@code /{id}}.
     */
    @GetMapping("/board")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<Page<FlightResponse>> getFlightBoard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "status", required = false) Set<FlightStatus> statuses,
            @RequestParam(required = false) String flightNumber,
            @RequestParam(required = false) UUID routeId,
            @PageableDefault(size = 50, sort = "departureTime", direction = Sort.Direction.ASC) Pageable pageable) {
        FlightBoardFilter filter = new FlightBoardFilter(date, statuses, flightNumber, routeId);
        return ApiResponse.success("Flights retrieved successfully", flightService.getFlightBoard(filter, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<FlightResponse> getFlight(@PathVariable UUID id) {
        return ApiResponse.success("Flight retrieved successfully", flightService.getFlight(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<FlightResponse> createFlight(@Valid @RequestBody FlightRequest request) {
        return ApiResponse.success("Flight created successfully", flightService.createFlight(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<FlightResponse> updateFlight(
            @PathVariable UUID id,
            @Valid @RequestBody FlightRequest request) {
        return ApiResponse.success("Flight updated successfully", flightService.updateFlight(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<FlightResponse> updateFlightStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFlightStatusRequest request) {
        return ApiResponse.success("Flight status updated successfully", flightService.updateFlightStatus(id, request.status()));
    }

    @PutMapping("/{id}/delay")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "Delay a flight: set the expected departure (the status becomes DELAYED)")
    public ApiResponse<FlightResponse> delayFlight(
            @PathVariable UUID id,
            @Valid @RequestBody FlightDelayRequest request) {
        return ApiResponse.success("Flight delayed", flightService.delayFlight(id, request.estimatedDepartureTime()));
    }

    @DeleteMapping("/{id}/delay")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "Lift a delay: the flight leaves at its scheduled time again")
    public ApiResponse<FlightResponse> clearDelay(@PathVariable UUID id) {
        return ApiResponse.success("Flight delay lifted", flightService.clearDelay(id));
    }

    @PutMapping("/{id}/gate")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "Assign or change the departure gate")
    public ApiResponse<FlightResponse> assignGate(
            @PathVariable UUID id,
            @Valid @RequestBody FlightGateRequest request) {
        return ApiResponse.success("Gate assigned", flightService.assignGate(id, request.gate()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> cancelFlight(@PathVariable UUID id) {
        flightService.cancelFlight(id);
        return ApiResponse.success("Flight cancelled successfully", null);
    }

    /**
     * Flight numbers of the aircraft's flights that are not cancelled or arrived yet (at most 5, earliest first):
     * the fleet service refuses to retire an aircraft that still has some.
     */
    @GetMapping("/aircraft/{aircraftId}/active")
    @PreAuthorize("hasRole('SERVICE')")
    public ApiResponse<List<String>> flightsOnAircraft(@PathVariable UUID aircraftId) {
        return ApiResponse.success("Flights on the aircraft", flightService.flightsOnAircraft(aircraftId, 5));
    }

    /**
     * Flight numbers of the given flights that are not cancelled or arrived and occupy their crew while the target
     * flight flies (delayed flights at their expected times): the crew service's overlap check.
     */
    @PostMapping("/overlapping")
    @PreAuthorize("hasRole('SERVICE')")
    public ApiResponse<List<String>> overlapping(@Valid @RequestBody OverlapRequest request) {
        return ApiResponse.success("Overlapping flights",
                flightService.overlapping(request.flightId(), request.flightIds()));
    }

    /** Gives a seat back without a claim: for bookings made before seat claims existed. */
    @PutMapping("/{id}/inventory/increment")
    @PreAuthorize("hasRole('SERVICE')")
    public void incrementSeat(@PathVariable UUID id) {
        flightService.incrementSeat(id);
    }

    /** Holds a seat for a booking, idempotently: the same claim id holds one seat however often it is sent. */
    @PutMapping("/{id}/inventory/claims/{claimId}")
    @PreAuthorize("hasRole('SERVICE')")
    public void claimSeat(@PathVariable UUID id, @PathVariable UUID claimId) {
        flightService.claimSeat(id, claimId);
    }

    /** Gives back the seat a claim holds; a claim that never landed (or was released) gives nothing back. */
    @DeleteMapping("/{id}/inventory/claims/{claimId}")
    @PreAuthorize("hasRole('SERVICE')")
    public void releaseSeat(@PathVariable UUID id, @PathVariable UUID claimId) {
        flightService.releaseSeat(id, claimId);
    }
}
