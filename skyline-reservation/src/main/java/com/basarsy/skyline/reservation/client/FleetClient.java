package com.basarsy.skyline.reservation.client;

import com.basarsy.skyline.reservation.client.dto.AircraftResponse;
import com.basarsy.skyline.reservation.client.dto.SeatLayout;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "fleet-service", url = "${fleet.service.url:http://localhost:8081}")
public interface FleetClient {

    @GetMapping("/api/v1/aircraft/{id}")
    AircraftResponse getAircraft(@PathVariable("id") UUID id);

    /** Every seat of an aircraft type; 404 (a SkylineException) when the type has no layout. */
    @GetMapping("/api/v1/aircraft-types/{id}/seat-layout")
    SeatLayout getSeatLayout(@PathVariable("id") UUID aircraftTypeId);
}
