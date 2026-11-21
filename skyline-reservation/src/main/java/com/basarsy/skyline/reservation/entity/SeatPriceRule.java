package com.basarsy.skyline.reservation.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * What one seat category costs a passenger of a given cabin and fare, before the nearly-full surcharge. Zero means
 * included in the fare. {@code autoAssign} marks the categories check-in may hand out for free to a passenger who
 * did not pick a seat (never the paid extra-legroom or exit rows).
 */
@Getter
@Setter
@Entity
@Table(name = "seat_price_rules")
public class SeatPriceRule extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "cabin_class", nullable = false)
    private CabinClass cabinClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "fare_type", nullable = false)
    private FareType fareType;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_category", nullable = false)
    private SeatCategory seatCategory;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "auto_assign", nullable = false)
    private boolean autoAssign;
}
