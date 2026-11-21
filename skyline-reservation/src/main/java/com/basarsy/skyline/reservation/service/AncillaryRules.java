package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.reservation.dto.AncillaryOffer;
import com.basarsy.skyline.reservation.entity.AncillaryPrice;
import com.basarsy.skyline.reservation.entity.AncillaryProduct;
import com.basarsy.skyline.reservation.entity.AncillaryType;
import com.basarsy.skyline.reservation.entity.SalesChannel;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What one passenger on one flight can still buy, and for how much. Pure: the catalog, prices, allowances and
 * purchases go in, the offers come out, so the rules are unit-tested without a database.
 *
 * <ul>
 *   <li>Every product has a per-passenger maximum that counts what the fare includes: a fare with a cabin bag
 *       can't buy another one.
 *   <li>At most {@value #MAX_CHECKED_BAGS} checked bags of any size, and at most one extra-weight top-up per
 *       checked bag (so a top-up needs a bag, included or bought). At most one meal.
 *   <li>Online sales close {@code cutoff_hours} before departure (24h for meals, 1h for the rest). At the airport
 *       the higher airport price applies, until departure; meals are pre-order only.
 * </ul>
 */
final class AncillaryRules {

    static final int MAX_CHECKED_BAGS = 3;
    static final int MAX_MEALS = 1;

    private AncillaryRules() {}

    /**
     * @param included  product code to quantity included in the fare
     * @param purchased product code to quantity already bought (active lines)
     */
    static List<AncillaryOffer> offers(List<AncillaryProduct> products, Map<String, AncillaryPrice> prices,
            Map<String, Integer> included, Map<String, Integer> purchased, LocalDateTime departure,
            LocalDateTime now, SalesChannel channel) {
        Map<AncillaryType, Integer> byType = new EnumMap<>(AncillaryType.class);
        Map<AncillaryType, Integer> includedByType = new EnumMap<>(AncillaryType.class);
        for (AncillaryProduct product : products) {
            int inc = included.getOrDefault(product.getCode(), 0);
            byType.merge(product.getType(), inc + purchased.getOrDefault(product.getCode(), 0), Integer::sum);
            includedByType.merge(product.getType(), inc, Integer::sum);
        }

        return products.stream()
                .filter(AncillaryProduct::isActive)
                .map(product -> offer(product, prices.get(product.getCode()), included, purchased, byType,
                        includedByType, departure, now, channel))
                .toList();
    }

    private static AncillaryOffer offer(AncillaryProduct product, AncillaryPrice price, Map<String, Integer> included,
            Map<String, Integer> purchased, Map<AncillaryType, Integer> byType,
            Map<AncillaryType, Integer> includedByType, LocalDateTime departure, LocalDateTime now,
            SalesChannel channel) {
        int checkedBags = byType.getOrDefault(AncillaryType.CHECKED_BAG, 0);
        int inc = included.getOrDefault(product.getCode(), 0);
        int bought = purchased.getOrDefault(product.getCode(), 0);
        int typeCount = byType.getOrDefault(product.getType(), 0);

        int room = product.getMaxQuantity() - inc - bought;
        String limitReason = inc > 0 && room <= 0 ? "Included in your fare" : "You have the maximum for this flight";
        switch (product.getType()) {
            case CHECKED_BAG -> room = Math.min(room, MAX_CHECKED_BAGS - typeCount);
            case MEAL -> {
                room = Math.min(room, MAX_MEALS - typeCount);
                if (room <= 0 && inc == 0 && typeCount > 0) {
                    limitReason = includedByType.getOrDefault(AncillaryType.MEAL, 0) > 0
                            ? "A meal is included in your fare" : "You already have a meal";
                }
            }
            case EXTRA_WEIGHT -> {
                room = Math.min(room, checkedBags - typeCount);
                if (checkedBags == 0) {
                    limitReason = "Add a checked bag first";
                }
            }
            default -> { }
        }

        String reason = null;
        if (price == null) {
            reason = "Not sold on this route";
        } else if (channel == SalesChannel.AIRPORT && product.getType() == AncillaryType.MEAL) {
            reason = "Meals can only be pre-ordered online";
        } else if (!now.isBefore(channel == SalesChannel.AIRPORT ? departure
                : departure.minusHours(product.getCutoffHours()))) {
            reason = channel == SalesChannel.AIRPORT ? "The flight has left"
                    : "Online sales closed " + product.getCutoffHours() + "h before departure";
        } else if (room <= 0) {
            reason = limitReason;
        }
        boolean available = reason == null;

        BigDecimal online = price == null ? null : price.getOnlinePrice();
        BigDecimal airport = price == null ? null : price.getAirportPrice();
        return new AncillaryOffer(product.getCode(), product.getType(), product.getName(), product.getDescription(),
                product.getWeightKg(), inc, bought, available ? room : 0,
                channel == SalesChannel.AIRPORT ? airport : online, online, airport, available, reason);
    }
}
