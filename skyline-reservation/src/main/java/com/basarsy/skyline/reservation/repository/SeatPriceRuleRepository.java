package com.basarsy.skyline.reservation.repository;

import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatCategory;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.entity.SeatPriceRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatPriceRuleRepository extends JpaRepository<SeatPriceRule, UUID> {

    List<SeatPriceRule> findByCabinClassAndFareType(CabinClass cabinClass, FareType fareType);

    Optional<SeatPriceRule> findByCabinClassAndFareTypeAndSeatCategory(
            CabinClass cabinClass, FareType fareType, SeatCategory seatCategory);

    List<SeatPriceRule> findAllByOrderByCabinClassAscFareTypeAscSeatCategoryAsc();
}
