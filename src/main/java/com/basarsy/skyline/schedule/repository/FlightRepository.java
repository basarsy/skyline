package com.basarsy.skyline.schedule.repository;

import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightRepository extends JpaRepository<Flight, UUID> {

    @Query("""
            SELECT f FROM Flight f
            WHERE f.route.origin.iataCode = :origin
              AND f.route.destination.iataCode = :destination
              AND f.departureTime >= :departureStart
              AND f.departureTime < :departureEnd
              AND f.status = :status
            """)
    Page<Flight> searchFlights(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureStart") LocalDateTime departureStart,
            @Param("departureEnd") LocalDateTime departureEnd,
            @Param("status") FlightStatus status,
            Pageable pageable);

    @Modifying
    @Query("UPDATE Flight f SET f.availableSeats = f.availableSeats - 1 WHERE f.id = :id AND f.availableSeats > 0")
    int decrementSeat(@Param("id") UUID id);
}
