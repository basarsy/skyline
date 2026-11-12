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

    boolean existsByPnr(String pnr);

    @Query("SELECT r.seatNumber FROM Reservation r WHERE r.flightId = :flightId AND r.seatNumber IS NOT NULL")
    java.util.Set<String> findAssignedSeats(@Param("flightId") UUID flightId);

    Page<Reservation> findByPassenger_UserId(UUID userId, Pageable pageable);

    java.util.List<Reservation> findByFlightId(UUID flightId);

    @Modifying
    // Also frees the seat: (flight_id, seat_number) is unique, so a cancelled booking must not keep holding it.
    @Query("UPDATE Reservation r SET r.status = :newStatus, r.seatNumber = null WHERE r.flightId = :flightId AND r.status != :newStatus")
    int updateStatusByFlightId(@Param("flightId") UUID flightId, @Param("newStatus") ReservationStatus newStatus);
}
