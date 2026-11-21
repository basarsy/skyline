package com.basarsy.skyline.reservation.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.reservation.dto.AncillaryPriceRequest;
import com.basarsy.skyline.reservation.dto.AncillaryProductRequest;
import com.basarsy.skyline.reservation.dto.AncillaryProductResponse;
import com.basarsy.skyline.reservation.entity.RouteScope;
import com.basarsy.skyline.reservation.service.AncillaryCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The add-on catalog: anyone signed in can read it (prices, what each fare includes); admins edit it. */
@RestController
@RequestMapping("/api/v1/ancillary-products")
@RequiredArgsConstructor
public class AncillaryCatalogController {

    private final AncillaryCatalogService catalog;

    @GetMapping
    @PreAuthorize("hasAnyRole('PASSENGER', 'STAFF', 'ADMIN')")
    public ApiResponse<List<AncillaryProductResponse>> findAll() {
        return ApiResponse.success(catalog.findAll());
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AncillaryProductResponse> update(@PathVariable String code,
            @Valid @RequestBody AncillaryProductRequest request) {
        return ApiResponse.success("Add-on updated", catalog.update(code, request));
    }

    @PutMapping("/{code}/prices/{scope}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AncillaryProductResponse> updatePrice(@PathVariable String code, @PathVariable RouteScope scope,
            @Valid @RequestBody AncillaryPriceRequest request) {
        return ApiResponse.success("Price updated", catalog.updatePrice(code, scope, request));
    }
}
