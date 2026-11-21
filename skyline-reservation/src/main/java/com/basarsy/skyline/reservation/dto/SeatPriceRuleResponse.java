package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatCategory;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.entity.SeatPriceRule;
import java.math.BigDecimal;
import java.util.UUID;

public record SeatPriceRuleResponse(
        UUID id, CabinClass cabinClass, FareType fareType, SeatCategory seatCategory, BigDecimal price,
        boolean autoAssign, String currency) {

    public static SeatPriceRuleResponse of(SeatPriceRule rule, String currency) {
        return new SeatPriceRuleResponse(rule.getId(), rule.getCabinClass(), rule.getFareType(),
                rule.getSeatCategory(), rule.getPrice(), rule.isAutoAssign(), currency);
    }
}
