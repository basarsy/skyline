package com.basarsy.skyline.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.reservation.client.dto.AircraftResponse;
import com.basarsy.skyline.reservation.client.dto.AircraftTypeSummary;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Legroom;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Seat;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatCategory;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatPosition;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.entity.SeatPriceRule;
import com.basarsy.skyline.reservation.repository.SeatPriceRuleRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Seat prices against the seeded price table (V3 migration values). */
class SeatPricingTest {

    private final SeatPriceRuleRepository repository = mock(SeatPriceRuleRepository.class);
    private final SeatPricing pricing = new SeatPricing(repository);
    private final List<SeatPriceRule> table = new ArrayList<>();

    @BeforeEach
    void seedTable() {
        rule(CabinClass.ECONOMY, FareType.BASIC, SeatCategory.STANDARD, "8.00", true);
        rule(CabinClass.ECONOMY, FareType.BASIC, SeatCategory.EXIT_ROW, "32.00", false);
        rule(CabinClass.ECONOMY, FareType.STANDARD, SeatCategory.STANDARD, "0.00", true);
        rule(CabinClass.ECONOMY, FareType.STANDARD, SeatCategory.PREFERRED, "6.00", true);
        rule(CabinClass.ECONOMY, FareType.STANDARD, SeatCategory.EXTRA_LEGROOM, "22.00", false);
        rule(CabinClass.ECONOMY, FareType.FLEX, SeatCategory.PREFERRED, "0.00", true);
        rule(CabinClass.BUSINESS, FareType.STANDARD, SeatCategory.EXTRA_LEGROOM, "0.00", true);
        when(repository.findByCabinClassAndFareType(any(), any())).thenAnswer(inv -> table.stream()
                .filter(r -> r.getCabinClass() == inv.getArgument(0) && r.getFareType() == inv.getArgument(1))
                .toList());
    }

    @Test
    void cheapFaresPayForEverySeat() {
        var prices = pricing.priceList(flight(180, 180), CabinClass.ECONOMY, FareType.BASIC);

        assertThat(prices.priceOf(seat(CabinClass.ECONOMY, SeatCategory.STANDARD))).isEqualByComparingTo("8.00");
        assertThat(prices.priceOf(seat(CabinClass.ECONOMY, SeatCategory.EXIT_ROW))).isEqualByComparingTo("32.00");
    }

    @Test
    void standardFareGetsAFreeStandardSeatButPaysForBetterOnes() {
        var prices = pricing.priceList(flight(180, 180), CabinClass.ECONOMY, FareType.STANDARD);

        assertThat(prices.priceOf(seat(CabinClass.ECONOMY, SeatCategory.STANDARD))).isZero();
        assertThat(prices.priceOf(seat(CabinClass.ECONOMY, SeatCategory.PREFERRED))).isEqualByComparingTo("6.00");
        assertThat(prices.priceOf(seat(CabinClass.ECONOMY, SeatCategory.EXTRA_LEGROOM))).isEqualByComparingTo("22.00");
    }

    @Test
    void businessIncludesSeatSelection() {
        var prices = pricing.priceList(flight(180, 180), CabinClass.BUSINESS, FareType.STANDARD);

        assertThat(prices.priceOf(seat(CabinClass.BUSINESS, SeatCategory.EXTRA_LEGROOM))).isZero();
    }

    @Test
    void nearlyFullFlightsRaisePaidSeatsButNotIncludedOnes() {
        // 170 of 180 sold = 94% -> +30%
        var prices = pricing.priceList(flight(180, 10), CabinClass.ECONOMY, FareType.STANDARD);

        assertThat(prices.surchargePercent()).isEqualTo(30);
        assertThat(prices.priceOf(seat(CabinClass.ECONOMY, SeatCategory.PREFERRED))).isEqualByComparingTo("7.80");
        assertThat(prices.priceOf(seat(CabinClass.ECONOMY, SeatCategory.STANDARD))).isZero();

        // 150 of 180 sold = 83% -> +15%, rounded to cents: 22 * 1.15 = 25.30
        var busy = pricing.priceList(flight(180, 30), CabinClass.ECONOMY, FareType.STANDARD);
        assertThat(busy.priceOf(seat(CabinClass.ECONOMY, SeatCategory.EXTRA_LEGROOM))).isEqualByComparingTo("25.30");
    }

    @Test
    void aSeatOutsideTheBookedCabinCannotBeBought() {
        var prices = pricing.priceList(flight(180, 180), CabinClass.ECONOMY, FareType.FLEX);
        Seat business = seat(CabinClass.BUSINESS, SeatCategory.EXTRA_LEGROOM);

        assertThat(prices.sells(business)).isFalse();
        assertThatThrownBy(() -> prices.priceOf(business))
                .isInstanceOf(SkylineException.class)
                .hasMessageContaining("you can only choose a seat in ECONOMY")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void aCategoryWithoutARuleIsNotForSale() {
        var prices = pricing.priceList(flight(180, 180), CabinClass.ECONOMY, FareType.FLEX);
        Seat exit = seat(CabinClass.ECONOMY, SeatCategory.EXIT_ROW);

        assertThat(prices.sells(exit)).isFalse();
        assertThatThrownBy(() -> prices.priceOf(exit)).hasMessageContaining("not for sale");
    }

    @Test
    void onlyAutoAssignCategoriesAreGivenAwayAtCheckIn() {
        var prices = pricing.priceList(flight(180, 180), CabinClass.ECONOMY, FareType.STANDARD);

        assertThat(prices.autoAssignable(seat(CabinClass.ECONOMY, SeatCategory.PREFERRED))).isTrue();
        assertThat(prices.autoAssignable(seat(CabinClass.ECONOMY, SeatCategory.EXTRA_LEGROOM))).isFalse();
        assertThat(prices.autoAssignable(seat(CabinClass.BUSINESS, SeatCategory.EXTRA_LEGROOM))).isFalse();
    }

    private void rule(CabinClass cabin, FareType fare, SeatCategory category, String price, boolean autoAssign) {
        SeatPriceRule rule = new SeatPriceRule();
        rule.setCabinClass(cabin);
        rule.setFareType(fare);
        rule.setSeatCategory(category);
        rule.setPrice(new BigDecimal(price));
        rule.setAutoAssign(autoAssign);
        table.add(rule);
    }

    static FlightResponse flight(int totalSeats, int availableSeats) {
        return FlightResponse.builder()
                .id(UUID.randomUUID())
                .availableSeats(availableSeats)
                .basePrice(new BigDecimal("100.00"))
                .aircraft(new AircraftResponse(UUID.randomUUID(), "TC-TST",
                        new AircraftTypeSummary(UUID.randomUUID(), "Airbus", "A320neo", totalSeats), null, 2020))
                .build();
    }

    private static Seat seat(CabinClass cabin, SeatCategory category) {
        return new Seat("1A", 1, "A", cabin, SeatPosition.WINDOW, Legroom.STANDARD, Set.of(), category);
    }
}
