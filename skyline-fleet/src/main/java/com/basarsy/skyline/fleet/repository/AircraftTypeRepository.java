package com.basarsy.skyline.fleet.repository;

import com.basarsy.skyline.fleet.entity.AircraftType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftTypeRepository extends JpaRepository<AircraftType, UUID> {

    boolean existsByManufacturerIgnoreCaseAndModelIgnoreCase(String manufacturer, String model);
}
