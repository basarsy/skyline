package com.basarsy.skyline.reservation.entity;

import java.math.BigDecimal;

/**
 * The fare bought within a cabin. It scales the fare price and decides what is included: seat selection, bags
 * and so on come from the seat price rules and the fare allowances, not from here.
 */
public enum FareType {
    /** Cheapest: seat selection and checked bags cost extra. */
    BASIC(new BigDecimal("0.85")),
    /** The default and the fare every booking made before fare types existed has. */
    STANDARD(BigDecimal.ONE),
    /** Most included, refundable add-ons. */
    FLEX(new BigDecimal("1.30"));

    private final BigDecimal multiplier;

    FareType(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public BigDecimal multiplier() {
        return multiplier;
    }
}
