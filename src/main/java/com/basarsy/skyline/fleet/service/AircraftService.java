package com.basarsy.skyline.fleet.service;

import com.basarsy.skyline.fleet.dto.AircraftRequest;
import com.basarsy.skyline.fleet.dto.AircraftResponse;
import com.basarsy.skyline.fleet.dto.UpdateAircraftStatusRequest;
import java.util.List;
import java.util.UUID;

public interface AircraftService {

    List<AircraftResponse> findAll();

    AircraftResponse findById(UUID id);

    AircraftResponse create(AircraftRequest request);

    AircraftResponse updateStatus(UUID id, UpdateAircraftStatusRequest request);
}
