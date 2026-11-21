package com.basarsy.skyline.reservation.repository;

import com.basarsy.skyline.reservation.entity.AncillaryType;
import com.basarsy.skyline.reservation.entity.ReservationAncillary;
import com.basarsy.skyline.reservation.entity.ReservationAncillary.Status;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationAncillaryRepository extends JpaRepository<ReservationAncillary, UUID> {

    List<ReservationAncillary> findByReservation_IdOrderByCreatedAtAsc(UUID reservationId);

    List<ReservationAncillary> findByReservation_IdAndStatus(UUID reservationId, Status status);

    Optional<ReservationAncillary> findByReservation_IdAndTypeAndStatus(UUID reservationId, AncillaryType type,
            Status status);

    /** The airline cancelled the flight: every active add-on on it is refunded, whatever the fare. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ReservationAncillary a
            SET a.status = com.basarsy.skyline.reservation.entity.ReservationAncillary.Status.REFUNDED,
                a.updatedAt = :now
            WHERE a.status = com.basarsy.skyline.reservation.entity.ReservationAncillary.Status.ACTIVE
            AND a.reservation.id IN (SELECT r.id FROM Reservation r WHERE r.flightId = :flightId)
            """)
    int refundAllOnFlight(@Param("flightId") UUID flightId, @Param("now") java.time.LocalDateTime now);

    @Query("""
            SELECT COALESCE(SUM(a.price), 0) FROM ReservationAncillary a
            WHERE a.reservation.id = :reservationId AND a.status = com.basarsy.skyline.reservation.entity.ReservationAncillary.Status.ACTIVE
            """)
    BigDecimal sumActive(@Param("reservationId") UUID reservationId);
}
