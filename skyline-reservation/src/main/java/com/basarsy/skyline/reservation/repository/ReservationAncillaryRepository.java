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

    @Query("""
            SELECT COALESCE(SUM(a.price), 0) FROM ReservationAncillary a
            WHERE a.reservation.id = :reservationId AND a.status = com.basarsy.skyline.reservation.entity.ReservationAncillary.Status.ACTIVE
            """)
    BigDecimal sumActive(@Param("reservationId") UUID reservationId);
}
