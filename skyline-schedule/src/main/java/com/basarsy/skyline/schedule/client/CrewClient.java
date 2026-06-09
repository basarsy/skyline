package com.basarsy.skyline.schedule.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "crew-service", url = "${crew.service.url:http://localhost:8086}")
public interface CrewClient {

    @GetMapping("/api/v1/crew/flight/{flightId}/validate")
    void validateCrewForFlight(@PathVariable("flightId") UUID flightId);
}
