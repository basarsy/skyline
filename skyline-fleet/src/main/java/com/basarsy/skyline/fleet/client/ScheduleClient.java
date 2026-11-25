package com.basarsy.skyline.fleet.client;

import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "schedule-service", url = "${schedule.service.url:http://localhost:8080}")
public interface ScheduleClient {

    /** Flight numbers of the aircraft's flights that are not cancelled or arrived yet (a few, earliest first). */
    @GetMapping("/api/v1/flights/aircraft/{aircraftId}/active")
    List<String> flightsOnAircraft(@PathVariable("aircraftId") UUID aircraftId);
}
