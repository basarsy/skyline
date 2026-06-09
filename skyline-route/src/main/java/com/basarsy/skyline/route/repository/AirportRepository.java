package com.basarsy.skyline.route.repository;

import com.basarsy.skyline.route.entity.Airport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportRepository extends JpaRepository<Airport, UUID> {

    Optional<Airport> findByIataCode(String iataCode);

    boolean existsByIataCode(String iataCode);
}
