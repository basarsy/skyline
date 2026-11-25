package com.basarsy.skyline.fleet.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.fleet.client.ScheduleClient;
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
    private final ScheduleClient scheduleClient;

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
    @CacheEvict(value = "aircrafts", allEntries = true)
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
        // ACTIVE and MAINTENANCE move freely between each other; RETIRED is final.
        if (aircraft.getStatus() == AircraftStatus.RETIRED && request.status() != AircraftStatus.RETIRED) {
            throw new SkylineException("A retired aircraft cannot return to service", HttpStatus.CONFLICT);
        }
        if (request.status() == AircraftStatus.RETIRED && aircraft.getStatus() != AircraftStatus.RETIRED) {
            requireRetirable(aircraft);
        }
        aircraft.setStatus(request.status());
        return aircraftMapper.toResponse(aircraft);
    }

    /**
     * Retiring is final, so it is an admin's call (like adding an aircraft), and only once the aircraft flies
     * nothing more: its flights would otherwise stay on sale and on the board with an aircraft that is gone.
     * Asks the schedule service; if it can't answer, the aircraft is not retired.
     */
    private void requireRetirable(Aircraft aircraft) {
        if (!CallerRoles.isAdmin()) {
            throw new SkylineException("Only an admin can retire an aircraft", HttpStatus.FORBIDDEN);
        }
        List<String> flights = scheduleClient.flightsOnAircraft(aircraft.getId());
        if (!flights.isEmpty()) {
            throw new SkylineException("Aircraft " + aircraft.getTailNumber() + " still flies "
                    + String.join(", ", flights) + ": move them to another aircraft or cancel them first",
                    HttpStatus.CONFLICT);
        }
    }
}
