package com.basarsy.skyline.reservation.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.reservation.dto.SeatMapResponse;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.service.SeatMapService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flights/{flightId}/seat-map")
@RequiredArgsConstructor
public class SeatMapController {

    private final SeatMapService seatMapService;

    /**
     * Layout, live availability and this passenger's prices. For a booked passenger pass {@code reservationId};
     * while booking pass the {@code cabin} and {@code fareType} being bought and the passenger's position (0-8).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PASSENGER', 'STAFF', 'ADMIN')")
    public ApiResponse<SeatMapResponse> getSeatMap(
            @PathVariable UUID flightId,
            @RequestParam(required = false) UUID reservationId,
            @RequestParam(required = false) CabinClass cabin,
            @RequestParam(required = false) FareType fareType,
            @RequestParam(defaultValue = "0") int passenger) {
        return ApiResponse.success("Seat map retrieved",
                seatMapService.getSeatMap(flightId, reservationId, cabin, fareType, passenger));
    }
}
