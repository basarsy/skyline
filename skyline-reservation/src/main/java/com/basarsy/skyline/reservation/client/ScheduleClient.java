package com.basarsy.skyline.reservation.client;

import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "schedule-service", url = "${schedule.service.url:http://localhost:8080}")
public interface ScheduleClient {

    @GetMapping("/api/v1/flights/{id}")
    FlightResponse getFlight(@PathVariable("id") UUID id);

    /** Holds one seat for {@code claimId}; repeating the call holds no second seat. */
    @PutMapping("/api/v1/flights/{id}/inventory/claims/{claimId}")
    void claimSeat(@PathVariable("id") UUID id, @PathVariable("claimId") UUID claimId);

    /** Gives back the seat {@code claimId} holds; nothing when it holds none. */
    @DeleteMapping("/api/v1/flights/{id}/inventory/claims/{claimId}")
    void releaseSeat(@PathVariable("id") UUID id, @PathVariable("claimId") UUID claimId);

    /** Gives a seat back without a claim: only for bookings made before claims existed. */
    @PutMapping("/api/v1/flights/{id}/inventory/increment")
    void incrementSeat(@PathVariable("id") UUID id);
}
