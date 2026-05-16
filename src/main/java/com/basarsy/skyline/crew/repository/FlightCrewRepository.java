package com.basarsy.skyline.crew.repository;

import com.basarsy.skyline.crew.entity.FlightCrew;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightCrewRepository extends JpaRepository<FlightCrew, UUID> {

    List<FlightCrew> findByFlightId(UUID flightId);
}
