package com.basarsy.skyline.checkin.client;

import com.basarsy.skyline.checkin.client.dto.ReservationResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "reservation-service", url = "${reservation.service.url:http://localhost:8084}")
public interface ReservationClient {

    @GetMapping("/api/v1/reservations/pnr/{pnr}")
    ReservationResponse getReservationByPnr(@PathVariable("pnr") String pnr);

    @GetMapping("/api/v1/reservations/{id}")
    ReservationResponse getReservation(@PathVariable("id") UUID id);
    
    // We'll need a way to update status from CONFIRMED to CHECKED_IN
    // For now, I'll assume an internal API exists or we'll add it
    @PutMapping("/api/v1/reservations/{id}/status")
    void updateStatus(@PathVariable("id") UUID id, @RequestParam("status") String status, @RequestParam("seatNumber") String seatNumber);
}
