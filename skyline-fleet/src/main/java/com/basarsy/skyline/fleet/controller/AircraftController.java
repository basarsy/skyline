package com.basarsy.skyline.fleet.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.fleet.dto.AircraftRequest;
import com.basarsy.skyline.fleet.dto.AircraftResponse;
import com.basarsy.skyline.fleet.dto.UpdateAircraftStatusRequest;
import com.basarsy.skyline.fleet.service.AircraftService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/aircraft")
@RequiredArgsConstructor
public class AircraftController {

    private final AircraftService aircraftService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AircraftResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(aircraftService.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<AircraftResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(aircraftService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AircraftResponse>> create(@Valid @RequestBody AircraftRequest request) {
        var created = aircraftService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<AircraftResponse>> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateAircraftStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aircraftService.updateStatus(id, request)));
    }
}
