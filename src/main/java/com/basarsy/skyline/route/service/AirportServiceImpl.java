package com.basarsy.skyline.route.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.route.dto.AirportRequest;
import com.basarsy.skyline.route.dto.AirportResponse;
import com.basarsy.skyline.route.mapper.AirportMapper;
import com.basarsy.skyline.route.repository.AirportRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AirportServiceImpl implements AirportService {

    private final AirportRepository airportRepository;
    private final AirportMapper airportMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AirportResponse> findAll() {
        return airportRepository.findAll().stream().map(airportMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AirportResponse findById(UUID id) {
        return airportRepository
                .findById(id)
                .map(airportMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Airport not found: " + id));
    }

    @Override
    @Transactional
    public AirportResponse create(AirportRequest request) {
        if (airportRepository.existsByIataCode(request.iataCode().toUpperCase())) {
            throw new SkylineException("Airport with IATA code already exists", HttpStatus.CONFLICT);
        }
        var airport = airportMapper.toEntity(request);
        airport.setIataCode(request.iataCode().toUpperCase());
        airport.setIcaoCode(request.icaoCode().toUpperCase());
        return airportMapper.toResponse(airportRepository.save(airport));
    }
}
