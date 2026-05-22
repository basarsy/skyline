package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.reservation.dto.ReservationRequest;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReservationService {
    ReservationResponse bookFlight(ReservationRequest request);
    ReservationResponse getReservation(UUID id);
    ReservationResponse getReservationByPnr(String pnr);
    Page<ReservationResponse> getMyReservations(Pageable pageable);
    void cancelReservation(UUID id);
}
