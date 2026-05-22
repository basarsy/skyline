package com.basarsy.skyline.reservation.repository;

import com.basarsy.skyline.reservation.entity.Passenger;
import com.basarsy.skyline.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassengerRepository extends JpaRepository<Passenger, UUID> {
    Optional<Passenger> findByUser(User user);
}

