package com.basarsy.skyline.fleet.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.fleet.dto.AircraftTypeRequest;
import com.basarsy.skyline.fleet.dto.AircraftTypeResponse;
import com.basarsy.skyline.fleet.service.AircraftTypeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/aircraft-types")
@RequiredArgsConstructor
public class AircraftTypeController {

    private final AircraftTypeService aircraftTypeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AircraftTypeResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(aircraftTypeService.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<AircraftTypeResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(aircraftTypeService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AircraftTypeResponse>> create(@Valid @RequestBody AircraftTypeRequest request) {
        var created = aircraftTypeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }
}
