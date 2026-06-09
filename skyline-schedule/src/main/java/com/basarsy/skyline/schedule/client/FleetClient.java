package com.basarsy.skyline.schedule.client;

import com.basarsy.skyline.schedule.client.dto.AircraftResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "fleet-service", url = "${fleet.service.url:http://localhost:8081}")
public interface FleetClient {

    @GetMapping("/api/v1/aircraft/{id}")
    AircraftResponse getAircraft(@PathVariable("id") UUID id);
}
