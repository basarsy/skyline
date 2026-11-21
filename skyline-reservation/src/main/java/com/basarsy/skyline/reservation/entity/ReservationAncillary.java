package com.basarsy.skyline.reservation.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * One paid extra on a reservation (one passenger on one flight): a seat, a bag, priority boarding, ... The
 * price is fixed when bought, so later price-table edits never change what was paid.
 */
@Getter
@Setter
@Entity
@Table(name = "reservation_ancillaries")
public class ReservationAncillary extends BaseEntity {

    public enum Status {
        ACTIVE,
        /** Replaced or removed before travel without a refund (e.g. a non-refundable seat changed). */
        CANCELLED,
        /** Removed and paid back, e.g. a refundable add-on on a cancelled booking. */
        REFUNDED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AncillaryType type;

    /** The catalog product (e.g. {@code CHECKED_BAG_20}) or, for seats, {@code SEAT_<category>}. */
    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(nullable = false)
    private String description;

    @Column(name = "seat_number", length = 8)
    private String seatNumber;

    @Column(nullable = false)
    private int quantity = 1;

    /** The line total (unit price x quantity) as charged. */
    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SalesChannel channel = SalesChannel.ONLINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(nullable = false)
    private boolean refundable;

    /** Ends an active line: paid back if it is refundable, otherwise kept by the airline. */
    public void end() {
        status = refundable ? Status.REFUNDED : Status.CANCELLED;
    }
}
