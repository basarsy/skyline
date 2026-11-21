package com.basarsy.skyline.reservation.repository;

import com.basarsy.skyline.reservation.entity.BlockedSeat;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockedSeatRepository extends JpaRepository<BlockedSeat, UUID> {

    @Query("SELECT b.seatNumber FROM BlockedSeat b WHERE b.flightId = :flightId")
    Set<String> findSeatNumbers(@Param("flightId") UUID flightId);
}
