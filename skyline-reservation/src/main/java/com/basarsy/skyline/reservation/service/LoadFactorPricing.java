package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.reservation.client.dto.AircraftResponse;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import java.math.BigDecimal;

/**
 * The nearly-full surcharge, shared by fares and paid seats: above 80% sold +15%, above 90% +30%. One place, so
 * the seat map's prices rise exactly when the fare's do.
 */
public final class LoadFactorPricing {

    static final BigDecimal NONE = BigDecimal.ONE;
    static final BigDecimal HIGH = new BigDecimal("1.15");
    static final BigDecimal VERY_HIGH = new BigDecimal("1.30");

    private LoadFactorPricing() {}

    public static BigDecimal multiplier(FlightResponse flight) {
        AircraftResponse aircraft = flight.getAircraft();
        int totalSeats = (aircraft != null && aircraft.aircraftType() != null)
                ? aircraft.aircraftType().totalSeats()
                : Math.max(flight.getAvailableSeats(), 180); // standard capacity when the aircraft isn't enriched
        int availableSeats = flight.getAvailableSeats();
        double loadFactor = totalSeats > 0 ? (double) (totalSeats - availableSeats) / totalSeats : 0.0;

        if (loadFactor > 0.90) {
            return VERY_HIGH;
        }
        if (loadFactor > 0.80) {
            return HIGH;
        }
        return NONE;
    }

    /** e.g. 15 for +15%, 0 without a surcharge. */
    public static int surchargePercent(BigDecimal multiplier) {
        return multiplier.subtract(BigDecimal.ONE).movePointRight(2).intValue();
    }
}
