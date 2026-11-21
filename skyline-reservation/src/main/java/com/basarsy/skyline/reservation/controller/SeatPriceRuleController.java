package com.basarsy.skyline.reservation.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.reservation.dto.SeatPriceRuleRequest;
import com.basarsy.skyline.reservation.dto.SeatPriceRuleResponse;
import com.basarsy.skyline.reservation.service.SeatPriceRuleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The seat price table: every cabin x fare x seat category. Admins edit prices; there is a row for each. */
@RestController
@RequestMapping("/api/v1/seat-price-rules")
@RequiredArgsConstructor
public class SeatPriceRuleController {

    private final SeatPriceRuleService seatPriceRuleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ApiResponse<List<SeatPriceRuleResponse>> findAll() {
        return ApiResponse.success(seatPriceRuleService.findAll());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SeatPriceRuleResponse> update(@PathVariable UUID id,
            @Valid @RequestBody SeatPriceRuleRequest request) {
        return ApiResponse.success("Seat price updated", seatPriceRuleService.update(id, request));
    }
}
