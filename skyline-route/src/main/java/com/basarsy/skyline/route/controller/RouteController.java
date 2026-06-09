package com.basarsy.skyline.route.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.route.dto.RouteRequest;
import com.basarsy.skyline.route.dto.RouteResponse;
import com.basarsy.skyline.route.service.RouteService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RouteResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(routeService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(routeService.findById(id)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<RouteResponse>>> search(
            @RequestParam String originIata,
            @RequestParam String destinationIata) {
        return ResponseEntity.ok(ApiResponse.success(routeService.findByOriginAndDestination(originIata, destinationIata)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RouteResponse>> create(@Valid @RequestBody RouteRequest request) {
        var created = routeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }
}
