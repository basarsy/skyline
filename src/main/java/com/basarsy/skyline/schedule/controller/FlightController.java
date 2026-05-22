package com.basarsy.skyline.schedule.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.schedule.dto.FlightRequest;
import com.basarsy.skyline.schedule.dto.FlightResponse;
import com.basarsy.skyline.schedule.dto.UpdateFlightStatusRequest;
import com.basarsy.skyline.schedule.service.FlightService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @GetMapping
    public ApiResponse<Page<FlightResponse>> searchFlights(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Pageable pageable) {
        return ApiResponse.success("Flights retrieved successfully", flightService.searchFlights(origin, destination, date, pageable));
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> cancelFlight(@PathVariable UUID id) {
        flightService.cancelFlight(id);
        return ApiResponse.success("Flight cancelled successfully", null);
    }
}
