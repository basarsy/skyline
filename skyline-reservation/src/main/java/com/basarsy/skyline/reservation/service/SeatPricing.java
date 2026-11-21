package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Seat;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatCategory;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.entity.SeatPriceRule;
import com.basarsy.skyline.reservation.repository.SeatPriceRuleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Seat prices, computed here only (a price from the client is never used). A seat costs its category's price for
 * the passenger's cabin and fare from {@code seat_price_rules}, times the flight's nearly-full surcharge; an
 * included seat (price 0) stays free however full the flight is. A passenger can only buy seats in the cabin
 * they booked.
 */
@Component
@RequiredArgsConstructor
public class SeatPricing {

    private final SeatPriceRuleRepository rules;

    /** The price list for one cabin and fare on one flight, read once and applied to many seats. */
    public PriceList priceList(FlightResponse flight, CabinClass cabin, FareType fare) {
        Map<SeatCategory, SeatPriceRule> byCategory = new EnumMap<>(SeatCategory.class);
        rules.findByCabinClassAndFareType(cabin, fare).forEach(rule -> byCategory.put(rule.getSeatCategory(), rule));
        return new PriceList(cabin, byCategory, LoadFactorPricing.multiplier(flight));
    }

    public record PriceList(CabinClass cabin, Map<SeatCategory, SeatPriceRule> rules, BigDecimal loadMultiplier) {

        /** The category's price, or null when no rule sells it (the seat is then not selectable). */
        public BigDecimal price(SeatCategory category) {
            SeatPriceRule rule = rules.get(category);
            if (rule == null) {
                return null;
            }
            return apply(rule.getPrice(), loadMultiplier);
        }

        /** Whether the seat can be bought by this passenger at all: their own cabin, and a rule prices it. */
        public boolean sells(Seat seat) {
            return seat.cabin() == cabin && rules.containsKey(seat.category());
        }

        /** The seat's price for this passenger; 422 when it's outside their cabin or not sold on their fare. */
        public BigDecimal priceOf(Seat seat) {
            if (seat.cabin() != cabin) {
                throw new SkylineException("Seat " + seat.code() + " is in " + seat.cabin()
                        + "; you can only choose a seat in " + cabin, HttpStatus.UNPROCESSABLE_ENTITY);
            }
            BigDecimal price = price(seat.category());
            if (price == null) {
                throw new SkylineException("Seat " + seat.code() + " is not for sale on your fare",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return price;
        }

        /** Whether check-in may hand this seat out for free to a passenger who did not choose one. */
        public boolean autoAssignable(Seat seat) {
            SeatPriceRule rule = rules.get(seat.category());
            return seat.cabin() == cabin && rule != null && rule.isAutoAssign();
        }

        public int surchargePercent() {
            return LoadFactorPricing.surchargePercent(loadMultiplier);
        }
    }

    static BigDecimal apply(BigDecimal price, BigDecimal loadMultiplier) {
        if (price.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return price.multiply(loadMultiplier).setScale(2, RoundingMode.HALF_UP);
    }
}
