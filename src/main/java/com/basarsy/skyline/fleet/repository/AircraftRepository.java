package com.basarsy.skyline.fleet.repository;

import com.basarsy.skyline.fleet.entity.Aircraft;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftRepository extends JpaRepository<Aircraft, UUID> {
}
