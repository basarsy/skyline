package com.basarsy.skyline.fleet.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.fleet.dto.AircraftTypeRequest;
import com.basarsy.skyline.fleet.dto.AircraftTypeResponse;
import com.basarsy.skyline.fleet.mapper.AircraftTypeMapper;
import com.basarsy.skyline.fleet.repository.AircraftTypeRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AircraftTypeServiceImpl implements AircraftTypeService {

    private final AircraftTypeRepository aircraftTypeRepository;
    private final AircraftTypeMapper aircraftTypeMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AircraftTypeResponse> findAll() {
        return aircraftTypeRepository.findAll().stream()
                .map(aircraftTypeMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AircraftTypeResponse findById(UUID id) {
        return aircraftTypeRepository
                .findById(id)
                .map(aircraftTypeMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft type not found: " + id));
    }

    @Override
    @Transactional
    public AircraftTypeResponse create(AircraftTypeRequest request) {
        if (aircraftTypeRepository.existsByManufacturerIgnoreCaseAndModelIgnoreCase(
                request.manufacturer(), request.model())) {
            throw new SkylineException("Aircraft type already exists", HttpStatus.CONFLICT);
        }
        return aircraftTypeMapper.toResponse(aircraftTypeRepository.save(aircraftTypeMapper.toEntity(request)));
    }
}
