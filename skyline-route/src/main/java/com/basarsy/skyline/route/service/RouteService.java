package com.basarsy.skyline.route.service;

import com.basarsy.skyline.route.dto.RouteRequest;
import com.basarsy.skyline.route.dto.RouteResponse;
import java.util.List;
import java.util.UUID;

public interface RouteService {

    List<RouteResponse> findAll();

    RouteResponse findById(UUID id);

    List<RouteResponse> findByOriginAndDestination(String originIata, String destinationIata);

    RouteResponse create(RouteRequest request);
}
