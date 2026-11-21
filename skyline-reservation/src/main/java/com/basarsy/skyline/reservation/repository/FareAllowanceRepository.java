package com.basarsy.skyline.reservation.repository;

import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareAllowance;
import com.basarsy.skyline.reservation.entity.FareType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareAllowanceRepository extends JpaRepository<FareAllowance, UUID> {

    List<FareAllowance> findByCabinClassAndFareType(CabinClass cabinClass, FareType fareType);
}
