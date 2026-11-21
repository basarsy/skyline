package com.basarsy.skyline.fleet.service;

import com.basarsy.skyline.fleet.dto.AircraftTypeRequest;
import com.basarsy.skyline.fleet.dto.AircraftTypeResponse;
import com.basarsy.skyline.fleet.layout.CabinLayout;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse;
import java.util.List;
import java.util.UUID;

public interface AircraftTypeService {

    List<AircraftTypeResponse> findAll();

    AircraftTypeResponse findById(UUID id);

    AircraftTypeResponse create(AircraftTypeRequest request);

    /** The layout as stored, for editing; 404 when the type has none yet. */
    CabinLayout getLayout(UUID id);

    /** Every seat of the type; 404 when the type has no layout yet. */
    SeatLayoutResponse getSeatLayout(UUID id);

    /** Validates a layout (its seat count must equal the type's total seats), stores it, returns it expanded. */
    SeatLayoutResponse updateLayout(UUID id, CabinLayout layout);
}
