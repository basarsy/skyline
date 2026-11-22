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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "reservations")
public class Reservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @Column(name = "flight_id", nullable = false)
    private UUID flightId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "seat_number", length = 8)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "cabin_class", nullable = false)
    private CabinClass cabinClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "fare_type", nullable = false)
    private FareType fareType = FareType.STANDARD;

    /** The ticket alone. */
    @Column(name = "fare_price", nullable = false)
    private BigDecimal farePrice;

    /** The fare plus every paid add-on (seat, bags, ...); kept in step by the add-on services. */
    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    /** Shared by the reservations booked together (several passengers and/or legs); null for a single booking. */
    @Column(name = "booking_group_id")
    private UUID bookingGroupId;

    @Column(name = "booked_at", nullable = false)
    private LocalDateTime bookedAt;

    @Column(nullable = false, unique = true, length = 6)
    private String pnr;

    /** Where messages about this booking go; null on bookings made before it was recorded. */
    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Version
    private Long version;
}
