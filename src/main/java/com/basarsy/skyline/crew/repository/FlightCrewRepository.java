package com.basarsy.skyline.crew.repository;

import com.basarsy.skyline.crew.entity.FlightCrew;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FlightCrewRepository extends JpaRepository<FlightCrew, UUID> {

    List<FlightCrew> findByFlightId(UUID flightId);

    List<FlightCrew> findByCrewMemberId(UUID crewMemberId);

    @Query("SELECT fc FROM FlightCrew fc JOIN fc.flight f WHERE fc.crewMember.id = :crewMemberId " +
           "AND f.departureTime < :arrivalTime AND f.arrivalTime > :departureTime")
    List<FlightCrew> findOverlappingAssignments(UUID crewMemberId, java.time.LocalDateTime departureTime, java.time.LocalDateTime arrivalTime);

    void deleteByFlightIdAndCrewMemberId(UUID flightId, UUID crewMemberId);
}
