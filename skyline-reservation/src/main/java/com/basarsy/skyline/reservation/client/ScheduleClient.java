package com.basarsy.skyline.reservation.client;

import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "schedule-service", url = "${schedule.service.url:http://localhost:8080}")
public interface ScheduleClient {

    @GetMapping("/api/v1/flights/{id}")
    FlightResponse getFlight(@PathVariable("id") UUID id);

    /** Gives back the seat {@code claimId} holds; nothing when it holds none. */
    @DeleteMapping("/api/v1/flights/{id}/inventory/claims/{claimId}")
    void releaseSeat(@PathVariable("id") UUID id, @PathVariable("claimId") UUID claimId);

    /** Holds a seat for each claim (a booking's passengers on this flight), all or none; repeating holds no more. */
    @PutMapping("/api/v1/flights/{id}/inventory/claims")
    void claimSeats(@PathVariable("id") UUID id, @RequestBody SeatClaims claims);

    /** Gives back the seats the claims hold; claims that never landed give nothing back. */
    @PostMapping("/api/v1/flights/{id}/inventory/claims/release")
    void releaseSeats(@PathVariable("id") UUID id, @RequestBody SeatClaims claims);

    record SeatClaims(List<UUID> claimIds) {}

    /** Gives a seat back without a claim: only for bookings made before claims existed. */
    @PutMapping("/api/v1/flights/{id}/inventory/increment")
    void incrementSeat(@PathVariable("id") UUID id);
}
