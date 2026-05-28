package com.basarsy.skyline.reservation.repository;

import com.basarsy.skyline.reservation.entity.Reservation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByPnr(String pnr);

    Page<Reservation> findByPassenger_Id(UUID passengerId, Pageable pageable);

    java.util.List<Reservation> findByFlight_Id(UUID flightId);
}
