package com.basarsy.skyline.route.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.route.dto.RouteRequest;
import com.basarsy.skyline.route.dto.RouteResponse;
import com.basarsy.skyline.route.entity.Route;
import com.basarsy.skyline.route.mapper.RouteMapper;
import com.basarsy.skyline.route.repository.AirportRepository;
import com.basarsy.skyline.route.repository.RouteRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;
    private final AirportRepository airportRepository;
    private final RouteMapper routeMapper;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "routes", key = "'all'")
    public List<RouteResponse> findAll() {
        return routeRepository.findAll().stream().map(routeMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "routes", key = "#id")
    public RouteResponse findById(UUID id) {
        return routeRepository
                .findById(id)
                .map(routeMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", allEntries = true)
    public RouteResponse create(RouteRequest request) {
        if (request.originAirportId().equals(request.destinationAirportId())) {
            throw new SkylineException("Origin and destination must differ", HttpStatus.BAD_REQUEST);
        }
        var origin = airportRepository
                .findById(request.originAirportId())
                .orElseThrow(() -> new ResourceNotFoundException("Origin airport not found"));
        var destination = airportRepository
                .findById(request.destinationAirportId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination airport not found"));

        var route = new Route();
        route.setOrigin(origin);
        route.setDestination(destination);
        route.setDistanceKm(request.distanceKm());
        route.setEstimatedDurationMinutes(request.estimatedDurationMinutes());
        return routeMapper.toResponse(routeRepository.save(route));
    }
}
