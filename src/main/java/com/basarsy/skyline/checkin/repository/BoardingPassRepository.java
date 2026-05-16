package com.basarsy.skyline.checkin.repository;

import com.basarsy.skyline.checkin.entity.BoardingPass;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardingPassRepository extends JpaRepository<BoardingPass, UUID> {

    Optional<BoardingPass> findByReservationPnr(String pnr);
}
