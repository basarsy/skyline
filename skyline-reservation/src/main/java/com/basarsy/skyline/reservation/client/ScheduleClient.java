package com.basarsy.skyline.reservation.client;

import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "schedule-service", url = "${schedule.service.url:http://localhost:8080}")
public interface ScheduleClient {

    @GetMapping("/api/v1/flights/{id}")
    FlightResponse getFlight(@PathVariable("id") UUID id);

    @PutMapping("/api/v1/flights/{id}/inventory/decrement")
    void decrementSeat(@PathVariable("id") UUID id);

    @PutMapping("/api/v1/flights/{id}/inventory/increment")
    void incrementSeat(@PathVariable("id") UUID id);
}
