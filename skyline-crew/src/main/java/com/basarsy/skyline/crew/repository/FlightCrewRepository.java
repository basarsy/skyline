package com.basarsy.skyline.crew.repository;

import com.basarsy.skyline.crew.entity.FlightCrew;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FlightCrewRepository extends JpaRepository<FlightCrew, UUID> {

    List<FlightCrew> findByFlightId(UUID flightId);

    List<FlightCrew> findByCrewMemberId(UUID crewMemberId);

    void deleteByFlightIdAndCrewMemberId(UUID flightId, UUID crewMemberId);
}
