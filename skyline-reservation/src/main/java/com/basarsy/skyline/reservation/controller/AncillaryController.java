package com.basarsy.skyline.reservation.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.reservation.dto.AncillaryOptionsResponse;
import com.basarsy.skyline.reservation.dto.AncillaryPurchaseRequest;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.entity.SalesChannel;
import com.basarsy.skyline.reservation.service.AncillaryService;
import com.basarsy.skyline.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bags and other add-ons for one reservation (one passenger, one flight): in manage booking and at check-in. */
@RestController
@RequestMapping("/api/v1/reservations/{id}/ancillaries")
@RequiredArgsConstructor
public class AncillaryController {

    private final AncillaryService ancillaryService;
    private final ReservationService reservationService;

    /** What can be bought, at what price, and what the fare already includes. */
    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('PASSENGER', 'STAFF', 'ADMIN')")
    public ApiResponse<AncillaryOptionsResponse> options(@PathVariable UUID id,
            @RequestParam(required = false) SalesChannel channel) {
        return ApiResponse.success(ancillaryService.options(id, channel));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PASSENGER', 'STAFF', 'ADMIN')")
    public ApiResponse<ReservationResponse> purchase(@PathVariable UUID id,
            @Valid @RequestBody AncillaryPurchaseRequest request) {
        ancillaryService.purchase(id, request);
        return ApiResponse.success("Add-ons added", reservationService.getReservation(id));
    }

    @DeleteMapping("/{lineId}")
    @PreAuthorize("hasAnyRole('PASSENGER', 'STAFF', 'ADMIN')")
    public ApiResponse<ReservationResponse> remove(@PathVariable UUID id, @PathVariable UUID lineId) {
        ancillaryService.remove(id, lineId);
        return ApiResponse.success("Add-on removed", reservationService.getReservation(id));
    }
}
