package com.basarsy.skyline.reservation.repository;

import com.basarsy.skyline.reservation.entity.Passenger;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassengerRepository extends JpaRepository<Passenger, UUID> {
    Optional<Passenger> findByUserId(UUID userId);
}
