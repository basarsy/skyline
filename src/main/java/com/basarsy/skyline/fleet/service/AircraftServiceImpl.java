package com.basarsy.skyline.fleet.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.fleet.dto.AircraftRequest;
import com.basarsy.skyline.fleet.dto.AircraftResponse;
import com.basarsy.skyline.fleet.dto.UpdateAircraftStatusRequest;
import com.basarsy.skyline.fleet.entity.Aircraft;
import com.basarsy.skyline.fleet.entity.AircraftStatus;
import com.basarsy.skyline.fleet.mapper.AircraftMapper;
import com.basarsy.skyline.fleet.repository.AircraftRepository;
import com.basarsy.skyline.fleet.repository.AircraftTypeRepository;
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
public class AircraftServiceImpl implements AircraftService {

    private final AircraftRepository aircraftRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final AircraftMapper aircraftMapper;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "aircrafts", key = "'all'")
    public List<AircraftResponse> findAll() {
        return aircraftRepository.findAllWithAircraftType().stream()
                .map(aircraftMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "aircrafts", key = "#id")
    public AircraftResponse findById(UUID id) {
        return aircraftRepository
                .findByIdWithAircraftType(id)
                .map(aircraftMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found: " + id));
    }

    @Override
    @Transactional
    public AircraftResponse create(AircraftRequest request) {
        var tailNumber = request.tailNumber().trim().toUpperCase();
        if (aircraftRepository.existsByTailNumberIgnoreCase(tailNumber)) {
            throw new SkylineException("Aircraft with tail number already exists", HttpStatus.CONFLICT);
        }

        var aircraftType = aircraftTypeRepository
                .findById(request.aircraftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft type not found"));

        var aircraft = new Aircraft();
        aircraft.setTailNumber(tailNumber);
        aircraft.setAircraftType(aircraftType);
        aircraft.setStatus(AircraftStatus.ACTIVE);
        aircraft.setManufacturedYear(request.manufacturedYear());

        return aircraftMapper.toResponse(aircraftRepository.save(aircraft));
    }

    @Override
    @Transactional
    @CacheEvict(value = "aircrafts", allEntries = true)
    public AircraftResponse updateStatus(UUID id, UpdateAircraftStatusRequest request) {
        var aircraft = aircraftRepository
                .findByIdWithAircraftType(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found: " + id));
        aircraft.setStatus(request.status());
        return aircraftMapper.toResponse(aircraft);
    }
}
