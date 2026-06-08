package com.basarsy.skyline.reservation.repository;

import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByPnr(String pnr);

    Page<Reservation> findByPassenger_Id(UUID passengerId, Pageable pageable);

    java.util.List<Reservation> findByFlight_Id(UUID flightId);

    @Modifying
    @Query("UPDATE Reservation r SET r.status = :newStatus WHERE r.flight.id = :flightId AND r.status != :newStatus")
    void cancelReservationsForFlight(@Param("flightId") UUID flightId, @Param("newStatus") ReservationStatus newStatus);
}
