package com.basarsy.skyline.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.basarsy.skyline.reservation.dto.AncillaryOffer;
import com.basarsy.skyline.reservation.entity.AncillaryPrice;
import com.basarsy.skyline.reservation.entity.AncillaryProduct;
import com.basarsy.skyline.reservation.entity.AncillaryType;
import com.basarsy.skyline.reservation.entity.RouteScope;
import com.basarsy.skyline.reservation.entity.SalesChannel;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Baggage allowance and price rules, with the seeded catalog's shape. */
class AncillaryRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 8, 0);
    private static final LocalDateTime DEPARTURE = NOW.plusHours(30);

    private final List<AncillaryProduct> catalog = List.of(
            product("CABIN_BAG", AncillaryType.CABIN_BAG, 1, 1),
            product("CHECKED_BAG_20", AncillaryType.CHECKED_BAG, 3, 1),
            product("CHECKED_BAG_30", AncillaryType.CHECKED_BAG, 3, 1),
            product("EXTRA_WEIGHT_10", AncillaryType.EXTRA_WEIGHT, 3, 1),
            product("MEAL_HOT", AncillaryType.MEAL, 1, 24),
            product("MEAL_VEGETARIAN", AncillaryType.MEAL, 1, 24));

    private final Map<String, AncillaryPrice> international = Map.of(
            "CABIN_BAG", price("CABIN_BAG", "25.00", "45.00"),
            "CHECKED_BAG_20", price("CHECKED_BAG_20", "40.00", "70.00"),
            "CHECKED_BAG_30", price("CHECKED_BAG_30", "60.00", "100.00"),
            "EXTRA_WEIGHT_10", price("EXTRA_WEIGHT_10", "25.00", "45.00"),
            "MEAL_HOT", price("MEAL_HOT", "15.00", "15.00"),
            "MEAL_VEGETARIAN", price("MEAL_VEGETARIAN", "15.00", "15.00"));

    @Test
    void aBasicFareBuysACabinBagButAFareThatIncludesOneCannotBuyItAgain() {
        AncillaryOffer basic = offer(Map.of(), Map.of(), SalesChannel.ONLINE, "CABIN_BAG");
        assertThat(basic.available()).isTrue();
        assertThat(basic.price()).isEqualByComparingTo("25.00");

        AncillaryOffer standard = offer(Map.of("CABIN_BAG", 1), Map.of(), SalesChannel.ONLINE, "CABIN_BAG");
        assertThat(standard.available()).isFalse();
        assertThat(standard.includedQuantity()).isEqualTo(1);
        assertThat(standard.reason()).isEqualTo("Included in your fare");
    }

    @Test
    void buyingOnlineIsCheaperThanAtTheAirport() {
        AncillaryOffer online = offer(Map.of(), Map.of(), SalesChannel.ONLINE, "CHECKED_BAG_20");
        AncillaryOffer airport = offer(Map.of(), Map.of(), SalesChannel.AIRPORT, "CHECKED_BAG_20");

        assertThat(online.price()).isEqualByComparingTo("40.00");
        assertThat(airport.price()).isEqualByComparingTo("70.00");
        assertThat(online.airportPrice()).isEqualByComparingTo(airport.price());
    }

    @Test
    void atMostThreeCheckedBagsOfAnySizeCountingIncludedOnes() {
        // FLEX: one 20 kg bag included, one 30 kg bought: one more of any size.
        Map<String, Integer> included = Map.of("CHECKED_BAG_20", 1);
        Map<String, Integer> purchased = Map.of("CHECKED_BAG_30", 1);
        assertThat(offer(included, purchased, SalesChannel.ONLINE, "CHECKED_BAG_20").maxAdditional()).isEqualTo(1);
        assertThat(offer(included, purchased, SalesChannel.ONLINE, "CHECKED_BAG_30").maxAdditional()).isEqualTo(1);

        AncillaryOffer full = offer(included, Map.of("CHECKED_BAG_30", 2), SalesChannel.ONLINE, "CHECKED_BAG_20");
        assertThat(full.available()).isFalse();
    }

    @Test
    void extraWeightNeedsACheckedBagAndOneTopUpPerBag() {
        AncillaryOffer noBag = offer(Map.of(), Map.of(), SalesChannel.ONLINE, "EXTRA_WEIGHT_10");
        assertThat(noBag.available()).isFalse();
        assertThat(noBag.reason()).isEqualTo("Add a checked bag first");

        assertThat(offer(Map.of("CHECKED_BAG_20", 1), Map.of(), SalesChannel.ONLINE, "EXTRA_WEIGHT_10").maxAdditional())
                .isEqualTo(1);
        assertThat(offer(Map.of("CHECKED_BAG_20", 1), Map.of("CHECKED_BAG_30", 1, "EXTRA_WEIGHT_10", 1),
                SalesChannel.ONLINE, "EXTRA_WEIGHT_10").maxAdditional()).isEqualTo(1);
    }

    @Test
    void oneMealAndAnIncludedMealCountsAsIt() {
        AncillaryOffer business = offer(Map.of("MEAL_HOT", 1), Map.of(), SalesChannel.ONLINE, "MEAL_VEGETARIAN");
        assertThat(business.available()).isFalse();
        assertThat(business.reason()).isEqualTo("A meal is included in your fare");
    }

    @Test
    void mealsArePreOrderOnlyAndCloseADayBeforeDeparture() {
        assertThat(offer(Map.of(), Map.of(), SalesChannel.AIRPORT, "MEAL_HOT").reason())
                .isEqualTo("Meals can only be pre-ordered online");

        AncillaryOffer late = AncillaryRules.offers(catalog, international, Map.of(), Map.of(), NOW.plusHours(20), NOW,
                SalesChannel.ONLINE).stream().filter(o -> o.code().equals("MEAL_HOT")).findFirst().orElseThrow();
        assertThat(late.available()).isFalse();
        assertThat(late.reason()).contains("24h before departure");
    }

    @Test
    void aProductWithoutAPriceOnThisRouteIsNotSold() {
        Map<String, AncillaryPrice> noCabinBag = new HashMap<>(international);
        noCabinBag.remove("CABIN_BAG");

        AncillaryOffer offer = AncillaryRules.offers(catalog, noCabinBag, Map.of(), Map.of(), DEPARTURE, NOW,
                SalesChannel.ONLINE).get(0);
        assertThat(offer.available()).isFalse();
        assertThat(offer.reason()).isEqualTo("Not sold on this route");
    }

    private AncillaryOffer offer(Map<String, Integer> included, Map<String, Integer> purchased, SalesChannel channel,
            String code) {
        return AncillaryRules.offers(catalog, international, included, purchased, DEPARTURE, NOW, channel).stream()
                .filter(o -> o.code().equals(code)).findFirst().orElseThrow();
    }

    private static AncillaryProduct product(String code, AncillaryType type, int max, int cutoffHours) {
        AncillaryProduct product = new AncillaryProduct();
        product.setCode(code);
        product.setType(type);
        product.setName(code);
        product.setDescription(code);
        product.setMaxQuantity(max);
        product.setCutoffHours(cutoffHours);
        product.setActive(true);
        return product;
    }

    private static AncillaryPrice price(String code, String online, String airport) {
        AncillaryPrice price = new AncillaryPrice();
        price.setProductCode(code);
        price.setRouteScope(RouteScope.INTERNATIONAL);
        price.setOnlinePrice(new BigDecimal(online));
        price.setAirportPrice(new BigDecimal(airport));
        return price;
    }
}
