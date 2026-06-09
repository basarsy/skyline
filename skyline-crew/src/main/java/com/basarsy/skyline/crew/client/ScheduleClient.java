package com.basarsy.skyline.crew.client;

import com.basarsy.skyline.crew.client.dto.FlightResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "schedule-service", url = "${schedule.service.url:http://localhost:8080}")
public interface ScheduleClient {

    @GetMapping("/api/v1/flights/{id}")
    FlightResponse getFlight(@PathVariable("id") UUID id);
}
