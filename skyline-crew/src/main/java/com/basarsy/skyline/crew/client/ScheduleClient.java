package com.basarsy.skyline.crew.client;

import com.basarsy.skyline.crew.client.dto.FlightResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "schedule-service", url = "${schedule.service.url:http://localhost:8080}")
public interface ScheduleClient {

    @GetMapping("/api/v1/flights/{id}")
    FlightResponse getFlight(@PathVariable("id") UUID id);

    /**
     * Flight numbers of the given flights (at most 1000) that are not cancelled or arrived and occupy their crew
     * while {@code flightId} flies (delayed flights at their expected times, worked out by the schedule service).
     */
    @PostMapping("/api/v1/flights/overlapping")
    List<String> overlapping(@RequestBody Overlap request);

    record Overlap(UUID flightId, List<UUID> flightIds) {}
}
