package com.basarsy.skyline.checkin.client;

import com.basarsy.skyline.checkin.client.dto.ReservationResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "reservation-service", url = "${reservation.service.url:http://localhost:8084}")
public interface ReservationClient {

    @GetMapping("/api/v1/reservations/pnr/{pnr}")
    ReservationResponse getReservationByPnr(@PathVariable("pnr") String pnr);

    /** Atomically assigns a seat and marks the reservation CHECKED_IN. Idempotent. */
    @PostMapping("/api/v1/reservations/{id}/check-in")
    ReservationResponse checkIn(@PathVariable("id") UUID id);
}
