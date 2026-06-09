package com.basarsy.skyline.schedule.client;

import com.basarsy.skyline.schedule.client.dto.RouteResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@FeignClient(name = "route-service", url = "${route.service.url:http://localhost:8082}")
public interface RouteClient {

    @GetMapping("/api/v1/routes/{id}")
    RouteResponse getRoute(@PathVariable("id") UUID id);

    @GetMapping("/api/v1/routes/search")
    List<RouteResponse> searchRoutes(@RequestParam("originIata") String originIata, @RequestParam("destinationIata") String destinationIata);
}
