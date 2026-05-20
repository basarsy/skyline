package com.basarsy.skyline.fleet.service;

import com.basarsy.skyline.fleet.dto.AircraftTypeRequest;
import com.basarsy.skyline.fleet.dto.AircraftTypeResponse;
import java.util.List;
import java.util.UUID;

public interface AircraftTypeService {

    List<AircraftTypeResponse> findAll();

    AircraftTypeResponse findById(UUID id);

    AircraftTypeResponse create(AircraftTypeRequest request);
}
