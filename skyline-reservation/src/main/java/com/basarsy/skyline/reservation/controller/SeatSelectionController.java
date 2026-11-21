package com.basarsy.skyline.reservation.controller;

import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.dto.SeatChoiceRequest;
import com.basarsy.skyline.reservation.dto.SeatHoldRequest;
import com.basarsy.skyline.reservation.dto.SeatHoldResponse;
import com.basarsy.skyline.reservation.service.ReservationService;
import com.basarsy.skyline.reservation.service.SeatSelectionService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seat selection. While booking: hold seats per passenger, then book with {@code POST /reservations/group}. For an
 * existing booking: hold a seat for the reservation, then confirm it with {@code PUT /reservations/{id}/seat}. A
 * seat someone else holds or has booked is a 409; the client then reloads the seat map.
 */
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class SeatSelectionController {

    private final SeatSelectionService seatSelectionService;
    private final ReservationService reservationService;

    @PostMapping("/seat-holds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PASSENGER')")
    public ApiResponse<SeatHoldResponse> holdForBooking(@Valid @RequestBody SeatHoldRequest request) {
        return ApiResponse.success("Seat held", seatSelectionService.holdForBooking(request));
    }

    @DeleteMapping("/seat-holds")
    @PreAuthorize("hasRole('PASSENGER')")
    public ApiResponse<Void> releaseForBooking(@RequestParam UUID flightId,
            @RequestParam(defaultValue = "0") int passenger) {
        seatSelectionService.releaseForBooking(flightId, passenger);
        return ApiResponse.success("Seat released", null);
    }

    @PostMapping("/{id}/seat-hold")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PASSENGER', 'STAFF', 'ADMIN')")
    public ApiResponse<SeatHoldResponse> holdForReservation(@PathVariable UUID id,
            @Valid @RequestBody SeatChoiceRequest request) {
        return ApiResponse.success("Seat held", seatSelectionService.holdForReservation(id, request.seatNumber()));
    }

    @DeleteMapping("/{id}/seat-hold")
    @PreAuthorize("hasAnyRole('PASSENGER', 'STAFF', 'ADMIN')")
    public ApiResponse<Void> releaseForReservation(@PathVariable UUID id) {
        seatSelectionService.releaseForReservation(id);
        return ApiResponse.success("Seat released", null);
    }

    /** Confirms the held seat (charging its fee as a line item) and returns the updated booking. */
    @PutMapping("/{id}/seat")
    @PreAuthorize("hasAnyRole('PASSENGER', 'STAFF', 'ADMIN')")
    public ApiResponse<ReservationResponse> confirmSeat(@PathVariable UUID id,
            @Valid @RequestBody SeatChoiceRequest request) {
        seatSelectionService.confirmSeat(id, request.seatNumber());
        return ApiResponse.success("Seat confirmed", reservationService.getReservation(id));
    }

    @DeleteMapping("/{id}/seat")
    @PreAuthorize("hasAnyRole('PASSENGER', 'STAFF', 'ADMIN')")
    public ApiResponse<ReservationResponse> removeSeat(@PathVariable UUID id) {
        seatSelectionService.removeSeat(id);
        return ApiResponse.success("Seat removed", reservationService.getReservation(id));
    }
}
