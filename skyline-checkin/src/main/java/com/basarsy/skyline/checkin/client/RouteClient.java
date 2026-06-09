package com.basarsy.skyline.checkin.client;

import com.basarsy.skyline.checkin.client.dto.RouteResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "route-service", url = "${route.service.url:http://localhost:8082}")
public interface RouteClient {

    @GetMapping("/api/v1/routes/{id}")
    RouteResponse getRoute(@PathVariable("id") UUID id);
}
