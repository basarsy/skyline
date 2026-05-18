package com.basarsy.skyline.route.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.route.dto.AirportRequest;
import com.basarsy.skyline.route.dto.AirportResponse;
import com.basarsy.skyline.route.service.AirportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/airports")
@RequiredArgsConstructor
public class AirportController {

    private final AirportService airportService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AirportResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(airportService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AirportResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(airportService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AirportResponse>> create(@Valid @RequestBody AirportRequest request) {
        var created = airportService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }
}
