package com.basarsy.skyline.fleet.repository;

import com.basarsy.skyline.fleet.entity.Aircraft;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AircraftRepository extends JpaRepository<Aircraft, UUID> {

    boolean existsByTailNumberIgnoreCase(String tailNumber);

    @Query("SELECT a FROM Aircraft a JOIN FETCH a.aircraftType")
    List<Aircraft> findAllWithAircraftType();

    @Query("SELECT a FROM Aircraft a JOIN FETCH a.aircraftType WHERE a.id = :id")
    Optional<Aircraft> findByIdWithAircraftType(UUID id);
}
