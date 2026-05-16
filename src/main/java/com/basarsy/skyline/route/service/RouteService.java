package com.basarsy.skyline.route.service;

import com.basarsy.skyline.route.dto.RouteRequest;
import com.basarsy.skyline.route.dto.RouteResponse;
import java.util.List;
import java.util.UUID;

public interface RouteService {

    List<RouteResponse> findAll();

    RouteResponse findById(UUID id);

    RouteResponse create(RouteRequest request);
}
