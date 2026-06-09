package com.basarsy.skyline.reservation.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.reservation.dto.ReservationRequest;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ReservationResponse> bookFlight(@Valid @RequestBody ReservationRequest request) {
        return ApiResponse.success("Flight booked successfully", reservationService.bookFlight(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'ADMIN')")
    public ApiResponse<ReservationResponse> getReservation(@PathVariable UUID id) {
        return ApiResponse.success("Reservation retrieved successfully", reservationService.getReservation(id));
    }

    @GetMapping("/pnr/{pnr}")
    public ApiResponse<ReservationResponse> getReservationByPnr(@PathVariable String pnr) {
        return ApiResponse.success("Reservation retrieved successfully", reservationService.getReservationByPnr(pnr));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Page<ReservationResponse>> getMyReservations(Pageable pageable) {
        return ApiResponse.success("Reservations retrieved successfully", reservationService.getMyReservations(pageable));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'ADMIN')")
    public ApiResponse<Void> cancelReservation(@PathVariable UUID id) {
        reservationService.cancelReservation(id);
        return ApiResponse.success("Reservation cancelled successfully", null);
    }

    @PutMapping("/{id}/status")
    public void updateStatus(
            @PathVariable UUID id, 
            @RequestParam com.basarsy.skyline.reservation.entity.ReservationStatus status,
            @RequestParam(required = false) String seatNumber) {
        reservationService.updateStatus(id, status, seatNumber);
    }
}
