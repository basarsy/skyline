package com.basarsy.skyline.reservation.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** How many of a product a cabin and fare already include (e.g. Economy FLEX: one 20 kg bag). */
@Getter
@Setter
@Entity
@Table(name = "fare_allowances")
public class FareAllowance extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "cabin_class", nullable = false)
    private CabinClass cabinClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "fare_type", nullable = false)
    private FareType fareType;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(nullable = false)
    private int quantity;
}
