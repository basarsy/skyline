package com.basarsy.skyline.reservation.repository;

import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import java.time.LocalDateTime;
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

    /**
     * Moves every reservation on a flight to {@code newStatus}. Also frees the seat: (flight_id, seat_number)
     * is unique, so a cancelled booking must not keep holding it. A bulk update skips auditing and optimistic
     * locking, so it sets {@code updatedAt} and bumps the version itself: a concurrent load-modify-save of
     * one of these reservations (e.g. a check-in) then conflicts instead of undoing the cancellation.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Reservation r
            SET r.status = :newStatus, r.seatNumber = null, r.version = r.version + 1, r.updatedAt = :now
            WHERE r.flightId = :flightId AND r.status <> :newStatus
            """)
    int updateStatusByFlightId(@Param("flightId") UUID flightId, @Param("newStatus") ReservationStatus newStatus,
            @Param("now") LocalDateTime now);
}
